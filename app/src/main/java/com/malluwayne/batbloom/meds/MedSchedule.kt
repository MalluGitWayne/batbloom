package com.malluwayne.batbloom.meds

import com.malluwayne.batbloom.data.MedEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** What a medication is; decides its picture and the word for one unit. */
enum class MedForm(val label: String, val unitWord: String) {
    Tablet("Tablet", "tablet"), Capsule("Capsule", "capsule"), Liquid("Liquid", "mL"), Drops("Drops", "drop"),
    Spray("Spray", "spray"), Inhaler("Inhaler", "puff"), Injection("Injection", "injection"), Cream("Cream", "application"),
    Patch("Patch", "patch"), Other("Other", "dose");

    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: Other }
}

enum class Frequency(val label: String) {
    Daily("Every day"), Weekdays("On specific days"), Interval("Every few days"), AsNeeded("As needed");

    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: Daily }
}

/** A scheduled time and how many units to take then. */
data class MedTime(val time: LocalTime, val qty: Double = 1.0)

/** One scheduled dose of one medication. [slot] identifies it in the log. */
data class MedDose(val med: MedEntity, val date: LocalDate, val time: LocalTime, val qty: Double) {
    val slot: String get() = slotOf(date, time)
    val at: LocalDateTime get() = date.atTime(time)
}

fun slotOf(date: LocalDate, time: LocalTime): String = "${date}T${"%02d:%02d".format(time.hour, time.minute)}"

object MedSchedule {
    val units = listOf("mg", "mcg", "g", "mL", "%", "IU", "units")

    fun parseTimes(s: String): List<MedTime> = s.split(';').mapNotNull { part ->
        val bits = part.trim().split('*')
        val t = runCatching { LocalTime.parse(bits[0].trim()) }.getOrNull() ?: return@mapNotNull null
        MedTime(t, bits.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0)
    }.sortedBy { it.time }

    fun formatTimes(l: List<MedTime>): String = l.sortedBy { it.time }.joinToString(";") {
        "%02d:%02d*%s".format(it.time.hour, it.time.minute, fmtQty(it.qty))
    }

    fun fmtQty(q: Double): String = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString().trimEnd('0').trimEnd('.')

    fun isActive(m: MedEntity, date: LocalDate): Boolean {
        if (m.archived) return false
        val start = runCatching { LocalDate.parse(m.startDate) }.getOrNull() ?: return false
        if (date.isBefore(start)) return false
        m.endDate?.let { e -> runCatching { LocalDate.parse(e) }.getOrNull()?.let { if (date.isAfter(it)) return false } }
        return true
    }

    /** Whether [m] has scheduled doses on [date] (never for as-needed medications). */
    fun occursOn(m: MedEntity, date: LocalDate): Boolean {
        if (!isActive(m, date)) return false
        return when (Frequency.of(m.frequency)) {
            Frequency.Daily -> true
            Frequency.Weekdays -> m.weekdays and (1 shl (date.dayOfWeek.value - 1)) != 0
            Frequency.Interval -> {
                val start = LocalDate.parse(m.startDate)
                ChronoUnit.DAYS.between(start, date) % m.intervalDays.coerceAtLeast(1) == 0L
            }
            Frequency.AsNeeded -> false
        }
    }

    fun dosesOn(meds: List<MedEntity>, date: LocalDate): List<MedDose> =
        meds.filter { occursOn(it, date) }
            .flatMap { m -> parseTimes(m.times).map { MedDose(m, date, it.time, it.qty) } }
            .sortedWith(compareBy<MedDose> { it.time }.thenBy { it.med.name.lowercase() })

    /** The first scheduled dose time strictly after [now], looking up to two weeks ahead. */
    fun nextTime(meds: List<MedEntity>, now: LocalDateTime): LocalDateTime? {
        for (i in 0..14) {
            val d = now.toLocalDate().plusDays(i.toLong())
            dosesOn(meds, d).map { it.at }.firstOrNull { it.isAfter(now) }?.let { return it }
        }
        return null
    }

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    fun summary(m: MedEntity): String {
        val times = parseTimes(m.times).joinToString(", ") { timeFmt.format(it.time) }
        return when (Frequency.of(m.frequency)) {
            Frequency.Daily -> "Every day · $times"
            Frequency.Weekdays -> weekdayLabel(m.weekdays) + " · $times"
            Frequency.Interval -> "Every ${m.intervalDays} days · $times"
            Frequency.AsNeeded -> "As needed"
        }
    }

    fun weekdayLabel(mask: Int): String = when (mask and 0b1111111) {
        0b1111111 -> "Every day"
        0b0011111 -> "Weekdays"
        0b1100000 -> "Weekends"
        else -> DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
    }

    /** "500 mg", or "" when no strength is set. */
    fun strengthLabel(m: MedEntity): String = if (m.strength > 0) "${fmtQty(m.strength)} ${m.unit}" else ""

    /** "1 tablet", "2 tablets", "5 mL". */
    fun qtyLabel(m: MedEntity, qty: Double): String {
        val f = MedForm.of(m.form)
        val n = fmtQty(qty)
        return if (f.unitWord == "mL") "$n mL" else "$n ${f.unitWord}" + if (qty == 1.0) "" else "s"
    }

    /** Units used per day on average over the next 7 days (for "stock lasts until"). */
    fun dailyUse(m: MedEntity, from: LocalDate): Double {
        val week = (0 until 7).map { from.plusDays(it.toLong()) }
        val total = week.filter { occursOn(m, it) }.sumOf { parseTimes(m.times).sumOf { t -> t.qty } }
        return total / 7.0
    }

    /** How many days the stock lasts at the scheduled rate, or null if not tracked / not scheduled. */
    fun daysOfStock(m: MedEntity, from: LocalDate): Int? {
        if (m.stock < 0) return null
        val use = dailyUse(m, from)
        if (use <= 0) return null
        return (m.stock / use).toInt()
    }
}
