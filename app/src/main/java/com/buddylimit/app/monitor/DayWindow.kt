package com.buddylimit.app.monitor

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The daily usage window. Budgets reset at [resetHour] (default 04:00 local) rather
 * than midnight, to avoid the "fresh budget at 12:01am" doomscroll loophole
 * (SYSTEM_DESIGN.md §6, decision D4).
 *
 * Pure java.time logic (no Android deps) so it is unit-testable. Phase 3 will make
 * the reset hour a user setting and zero counters on crossing [nextReset]; for now
 * this just buckets usage by the correct day so accumulation is anchored correctly.
 */
object DayWindow {
    const val DEFAULT_RESET_HOUR = 4

    private val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Start of the current window: the most recent [resetHour]:00 at or before [nowMillis]. */
    fun dayStart(nowMillis: Long, zone: ZoneId, resetHour: Int = DEFAULT_RESET_HOUR): ZonedDateTime {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val todaysReset = now.toLocalDate().atTime(LocalTime.of(resetHour, 0)).atZone(zone)
        return if (now.isBefore(todaysReset)) todaysReset.minusDays(1) else todaysReset
    }

    /**
     * Stable key identifying the current window (the calendar date the window opened on).
     * Usage counters are bucketed by this so a window survives process death and restart.
     */
    fun dayKey(nowMillis: Long, zone: ZoneId, resetHour: Int = DEFAULT_RESET_HOUR): String =
        dayStart(nowMillis, zone, resetHour).toLocalDate().format(KEY_FORMAT)

    /** Epoch millis of the next reset boundary after [nowMillis] (what the overlay counts down to). */
    fun nextReset(nowMillis: Long, zone: ZoneId, resetHour: Int = DEFAULT_RESET_HOUR): Long =
        dayStart(nowMillis, zone, resetHour).plusDays(1).toInstant().toEpochMilli()
}
