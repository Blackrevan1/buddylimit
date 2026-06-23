package com.buddylimit.app.monitor

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DayWindowTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `after reset hour, key is today`() {
        // 10:00 on Jun 23 -> window opened at 04:00 Jun 23
        val now = millis(2026, 6, 23, 10, 0)
        assertEquals("2026-06-23", DayWindow.dayKey(now, zone))
    }

    @Test
    fun `before reset hour, key is previous day`() {
        // 02:00 on Jun 23 is before 04:00 -> still the window that opened 04:00 Jun 22
        val now = millis(2026, 6, 23, 2, 0)
        assertEquals("2026-06-22", DayWindow.dayKey(now, zone))
    }

    @Test
    fun `exactly at reset hour, new window has started`() {
        val now = millis(2026, 6, 23, 4, 0)
        assertEquals("2026-06-23", DayWindow.dayKey(now, zone))
    }

    @Test
    fun `one minute before reset still belongs to previous window`() {
        val now = millis(2026, 6, 23, 3, 59)
        assertEquals("2026-06-22", DayWindow.dayKey(now, zone))
    }

    @Test
    fun `next reset is the upcoming reset hour`() {
        val now = millis(2026, 6, 23, 10, 0)
        assertEquals(millis(2026, 6, 24, 4, 0), DayWindow.nextReset(now, zone))
    }

    @Test
    fun `next reset before reset hour is today's reset hour`() {
        val now = millis(2026, 6, 23, 2, 0)
        assertEquals(millis(2026, 6, 23, 4, 0), DayWindow.nextReset(now, zone))
    }

    @Test
    fun `custom reset hour is honored`() {
        // With a midnight reset, 02:00 belongs to the day that opened 00:00 the same date.
        val now = millis(2026, 6, 23, 2, 0)
        assertEquals("2026-06-23", DayWindow.dayKey(now, zone, resetHour = 0))
    }
}
