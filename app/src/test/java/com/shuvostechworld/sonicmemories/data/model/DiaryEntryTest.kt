package com.shuvostechworld.sonicmemories.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryEntryTest {

    @Test
    fun `test default values`() {
        val entry = DiaryEntry()
        assertEquals("", entry.id)
        assertEquals("", entry.title)
        assertEquals(0L, entry.timestamp)
        assertEquals(true, entry.synced)
    }

    @Test
    fun `test custom values`() {
        val entry = DiaryEntry(
            id = "123",
            title = "My Memory",
            content = "This is a test content",
            mood = 5,
            timestamp = 1000L,
            synced = false
        )
        assertEquals("123", entry.id)
        assertEquals("My Memory", entry.title)
        assertEquals("This is a test content", entry.content)
        assertEquals(5, entry.mood)
        assertEquals(1000L, entry.timestamp)
        assertEquals(false, entry.synced)
    }
}
