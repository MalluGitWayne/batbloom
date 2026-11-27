package com.malluwayne.batbloom.meds

import com.malluwayne.batbloom.data.DoseLogEntity
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.data.MedLogEntity
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.quit.Doses
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line of the medication history: a logged dose of a medication, or of the varenicline course. */
data class HistoryEntry(
    /** The medication's id, or null for varenicline. */
    val medId: Long?,
    val name: String,
    /** Strength and amount, e.g. "500 mg · 1 tablet". */
    val detail: String,
    val status: String,
    /** When it was taken (or skipped). */
    val actedAt: LocalDateTime,
    /** The scheduled time, or null for an as-needed dose. */
    val scheduled: LocalDateTime?,
) {
    /** The day it belongs to: its scheduled day (a late evening dose taken after midnight stays on its day). */
    val day: LocalDate get() = (scheduled ?: actedAt).toLocalDate()

    /** Minutes after the scheduled time it was taken (negative when early); null if as-needed or skipped. */
    val minutesLate: Long? get() = if (status == DoseStatus.TAKEN && scheduled != null) Duration.between(scheduled, actedAt).toMinutes() else null
}

/** Builds the history from both logs, newest first. Pure, so it's tested. */
object MedHistory {
    const val VARENICLINE = "Varenicline"

    fun entries(meds: List<MedEntity>, logs: List<MedLogEntity>, course: List<DoseLogEntity>, schedule: DoseSchedule, zone: ZoneId): List<HistoryEntry> {
        val byId = meds.associateBy { it.id }
        fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
        val medEntries = logs.mapNotNull { l ->
            val m = byId[l.medId] ?: return@mapNotNull null
            HistoryEntry(
                m.id, m.name,
                listOf(MedSchedule.strengthLabel(m), MedSchedule.qtyLabel(m, l.quantity)).filter { it.isNotBlank() }.joinToString(" · "),
                l.status, at(l.actedAt), l.slot?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            )
        }
        val courseEntries = course.mapNotNull { d ->
            // Keys look like "2026-11-21-AM".
            val date = runCatching { LocalDate.parse(d.key.take(10)) }.getOrNull() ?: return@mapNotNull null
            val time = if (d.key.endsWith("AM")) schedule.amTime else schedule.pmTime
            HistoryEntry(
                null, VARENICLINE,
                "${Doses.formatMg(d.mg)} · ${Doses.formatTablets(d.tablets)} · day ${d.day}",
                d.status, at(d.actedAt), date.atTime(time),
            )
        }
        return (medEntries + courseEntries).sortedWith(compareByDescending<HistoryEntry> { it.day }.thenByDescending { it.scheduled ?: it.actedAt })
    }

    /** Taken and due among scheduled doses (as-needed ones don't count toward "taken on time"). */
    fun takenShare(entries: List<HistoryEntry>): Pair<Int, Int> {
        val scheduled = entries.filter { it.scheduled != null }
        return scheduled.count { it.status == DoseStatus.TAKEN } to scheduled.size
    }

    private val csvDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val csvTime = DateTimeFormatter.ofPattern("HH:mm")

    /** A spreadsheet-ready copy (for a doctor or pharmacist). */
    fun csv(entries: List<HistoryEntry>): String = buildString {
        append("Date,Medication,Dose,Status,Scheduled,Taken at\n")
        entries.forEach { e ->
            fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            append(listOf(
                csvDate.format(e.day), q(e.name), q(e.detail),
                if (e.status == DoseStatus.TAKEN) "Taken" else "Skipped",
                e.scheduled?.let { csvTime.format(it) } ?: "as needed",
                if (e.status == DoseStatus.TAKEN) csvDate.format(e.actedAt) + " " + csvTime.format(e.actedAt) else "",
            ).joinToString(","))
            append('\n')
        }
    }
}
