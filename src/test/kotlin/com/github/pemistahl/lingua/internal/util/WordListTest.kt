package com.github.pemistahl.lingua.internal.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WordListTest {
    private fun getWords(text: String): List<String> {
        val wordList = WordList.build(text)
        val words = mutableListOf<String>()
        wordList.forEach { word ->
            // CharSequence currently intentionally does not implement `toString` since that would perform
            // allocation every time; therefore manually build string here
            val wordBuilder = StringBuilder(word.length)
            word.forEach(wordBuilder::append)
            words.add(wordBuilder.toString())
        }

        return words
    }

    @Test
    fun testWords() {
        assertThat(getWords("this is a sentence")).isEqualTo(listOf("this", "is", "a", "sentence"))

        assertThat(
            getWords("上海大学是一个好大学 this is a sentence"),
        ).isEqualTo(listOf("上", "海", "大", "学", "是", "一", "个", "好", "大", "学", "this", "is", "a", "sentence"))
    }

    @Test
    fun testToStringUnsupported() {
        lateinit var word: CharSequence
        WordList.build("test").forEach { word = it }

        // CharSequence implementation currently intentionally does not support `toString` and throws exception
        // to detect its usage, since that would cause allocation (which is undesired for every word during detection)
        assertThrows<UnsupportedOperationException> { word.toString() }
    }
}
