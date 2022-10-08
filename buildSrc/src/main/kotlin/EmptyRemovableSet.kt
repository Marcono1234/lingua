import java.util.Collections

/**
 * Empty set which is immutable but whose [remove] function simply returns `false` instead of throwing
 * an exception.
 */
internal object EmptyRemovableSet: AbstractMutableSet<Any>() {
    override fun add(element: Any): Boolean {
        throw UnsupportedOperationException()
    }

    override val size: Int
        get() = 0

    override fun iterator(): MutableIterator<Any> = Collections.emptyIterator()

    override fun remove(element: Any): Boolean {
        return false
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <E> emptyRemovableSet(): MutableSet<E> = EmptyRemovableSet as MutableSet<E>
