import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

/**
 * Concurrently runs the model creation and model writing tasks.
 */
internal class ModelFileCreator {
    /**
     * Custom closeable blocking queue wrapping a [java.util.concurrent.BlockingQueue]. This class exists
     * because Java's `BlockingQueue` does not support closing the queue.
     */
    private class CloseableBlockingQueue<E>(capacity: Int) {
        data class ItemHolder<E>(
            /** `null` means the queue is closed */
            val data: E?
        )

        @Volatile
        private var isClosed = false
        private val queue = ArrayBlockingQueue<ItemHolder<E>>(capacity)

        /**
         * Puts an item in the queue, blocking if the queue is currently full.
         */
        fun put(e: E) {
            check(!isClosed)
            queue.put(ItemHolder(e))
        }

        /**
         * Takes an item in the queue, blocking if the queue is currently empty.
         * Returns `null` if the queue is closed.
         */
        fun take(): E? {
            val holder = queue.take()
            if (holder.data == null) {
                // Put pack empty holder so next take() call sees empty holder as well
                queue.put(holder)
            }
            return holder.data
        }

        /**
         * Closes the queue. This prevents [put] from accepting new items.
         */
        fun close() {
            isClosed = true
            queue.put(ItemHolder(null))
        }

        /**
         * Force closes the queue, possibly discarding some remaining items.
         */
        fun forceClose() {
            isClosed = true
            while (!queue.offer(ItemHolder(null))) {
                // Make space in the queue
                queue.poll()
            }
        }
    }

    private data class ModelTask<M>(val modelCreator: () -> M, val modelWriter: (model: M) -> Unit) {
        fun createModel(): Runnable {
            val model = modelCreator()
            return Runnable {
                modelWriter(model)
            }
        }
    }

    // Set capacity to avoid too many pending tasks taking up too much memory
    private val modelCreationQueue = CloseableBlockingQueue<ModelTask<*>>(20)
    private val modelWriteQueue = CloseableBlockingQueue<Runnable>(15)
    private val modelCreatorThreads: Array<Thread>
    // Only use a single thread for writing because parallelizing IO probably does not increase performance
    private val modelWriterThread: Thread
    private val threadExceptions: Queue<Throwable> = ConcurrentLinkedQueue()
    private var allowsNewTasks = true

    init {
        // Note: These threads might cause decreased performance when Gradle is running other tasks in parallel;
        // has to be checked if it is worth using Gradle's WorkerExecutor for these CPU-bound tasks
        modelCreatorThreads = Array(max(1, Runtime.getRuntime().availableProcessors() - 1)) { threadIndex ->
            Thread(
                {
                    try {
                        while (true) {
                            val task = modelCreationQueue.take() ?: break
                            modelWriteQueue.put(task.createModel())
                        }
                    } catch (t: Throwable) {
                        threadExceptions.add(t)
                    }
                },
                "model-creator-${threadIndex + 1}"
            ).also {
                it.isDaemon = true
                it.start()
            }
        }
        modelWriterThread = Thread(
            {
                try {
                    while (true) {
                        val task = modelWriteQueue.take() ?: break
                        task.run()
                    }
                } catch (t: Throwable) {
                    threadExceptions.add(t)
                    modelCreationQueue.forceClose()
                }
            },
            "model-writer"
        ).also {
            it.isDaemon = true
            it.start()
        }
    }

    /**
     * Submits a model creation task. [modelCreator] and [modelWriter] run asynchronously.
     * Blocks if too many tasks are currently pending. [modelCreator] should be a CPU-bound
     * task, [modelWriter] should be a disk IO-bound task.
     */
    fun <M> submitCreationTask(modelCreator: () -> M, modelWriter: (model: M) -> Unit) {
        check(allowsNewTasks)
        // Blocks if too many tasks are pending
        modelCreationQueue.put(ModelTask(modelCreator, modelWriter))
    }

    /**
     * Prevents [submitCreationTask] from accepting new tasks and waits until all tasks are
     * completed or the worker threads fail with an exception.
     */
    fun awaitCompletion() {
        allowsNewTasks = false
        modelCreationQueue.close()
        modelCreatorThreads.forEach(Thread::join)
        modelWriteQueue.close()
        modelWriterThread.join()

        if (threadExceptions.isNotEmpty()) {
            throw Exception("Execution failed").also {
                threadExceptions.forEach(it::addSuppressed)
            }
        }
    }
}
