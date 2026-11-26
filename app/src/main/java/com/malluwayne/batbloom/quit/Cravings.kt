package com.malluwayne.batbloom.quit

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One craving: when, and what set it off (blank when not said). */
data class Craving(val at: Long, val trigger: String)

/** Cravings logged from "Craving? Breathe": stored as "millis|trigger" strings, pure so it can be tested. */
object Cravings {
    val triggers = listOf("Stress", "Coffee", "After food", "Alcohol", "Boredom", "Others smoking")
    const val KEEP = 500

    fun format(c: Craving) = "${c.at}|${c.trigger.replace("|", " ")}"

    fun parse(s: String): Craving? {
        val i = s.indexOf('|')
        val at = (if (i < 0) s else s.substring(0, i)).toLongOrNull() ?: return null
        return Craving(at, if (i < 0) "" else s.substring(i + 1))
    }

    /** Cravings per day for the [days] days ending [today], oldest first. */
    fun perDay(all: List<Craving>, today: LocalDate, zone: ZoneId, days: Int = 14): List<Int> {
        val counts = all.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.eachCount()
        return (days - 1 downTo 0).map { counts[today.minusDays(it.toLong())] ?: 0 }
    }

    /** The trigger named most often (ties: the latest), or null when none was given. */
    fun topTrigger(all: List<Craving>): String? =
        all.filter { it.trigger.isNotBlank() }.groupBy { it.trigger }
            .maxWithOrNull(compareBy<Map.Entry<String, List<Craving>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.at } })?.key
}
