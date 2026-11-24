package com.malluwayne.batbloom.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.meds.Frequency
import com.malluwayne.batbloom.meds.MedForm
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.quit.Doses
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A one-page (or more) PDF of your current medications, to show a doctor or pharmacist. */
object MedListPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    data class Row(val name: String, val detail: String, val schedule: String, val notes: String?)

    fun rows(meds: List<MedEntity>, course: DoseSchedule, today: LocalDate): List<Row> = buildList {
        val day = course.dayNumber(today)
        if (day in 1..course.courseDays) {
            val (am, pm) = course.dosesOnDay(day)
            add(
                Row(
                    "Varenicline", "Tablet · quit-smoking course",
                    "Day $day of ${course.courseDays} · now " + listOfNotNull(
                        if (am > 0) "${Doses.formatMg(am)} morning" else null, if (pm > 0) "${Doses.formatMg(pm)} evening" else null,
                    ).joinToString(", "),
                    "Started ${course.start}, ends ${course.endDate}",
                ),
            )
        }
        meds.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { m ->
            val sched = if (Frequency.of(m.frequency) == Frequency.Taper) {
                MedSchedule.parsePhases(m.times).joinToString("; ") { p ->
                    "days ${p.from}-${p.to}: " + p.times.joinToString(", ") { "${MedSchedule.qtyLabel(m, it.qty)} at ${it.time}" }
                }
            } else if (Frequency.of(m.frequency) == Frequency.AsNeeded) "As needed"
            else MedSchedule.summary(m) + " (" + MedSchedule.parseTimes(m.times).joinToString(", ") { MedSchedule.qtyLabel(m, it.qty) } + ")"
            add(
                Row(
                    m.name,
                    listOf(MedForm.of(m.form).label, MedSchedule.strengthLabel(m)).filter { it.isNotBlank() }.joinToString(" · "),
                    sched + " · since ${m.startDate}" + (m.endDate?.let { " until $it" } ?: ""),
                    m.notes,
                ),
            )
        }
    }

    fun write(file: File, rows: List<Row>, today: LocalDate) {
        val doc = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = 0xFF333333.toInt() }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF777777.toInt() }
        val line = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.8f }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas
        var y = M + 10
        fun header() {
            c.drawText("My medications", M, y, title); y += 18
            c.drawText("Made with Batbloom on ${DateTimeFormatter.ofPattern("d MMMM yyyy").format(today)}", M, y, muted); y += 22
        }
        header()
        fun wrap(text: String, p: Paint, width: Float): List<String> {
            val out = mutableListOf<String>()
            var cur = ""
            text.split(" ").forEach { w ->
                val next = if (cur.isEmpty()) w else "$cur $w"
                if (p.measureText(next) > width && cur.isNotEmpty()) { out += cur; cur = w } else cur = next
            }
            if (cur.isNotEmpty()) out += cur
            return out
        }
        if (rows.isEmpty()) c.drawText("No medications added.", M, y, body)
        rows.forEach { r ->
            val lines = wrap(r.schedule, body, W - 2 * M) + (r.notes?.takeIf { it.isNotBlank() }?.let { wrap("Notes: $it", muted, W - 2 * M) } ?: emptyList())
            val need = 16f + 14f + lines.size * 13f + 10f
            if (y + need > H - M) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); c = page.canvas; y = M + 10
            }
            c.drawText(r.name, M, y, bold)
            c.drawText(r.detail, M + bold.measureText(r.name) + 10, y, muted); y += 16
            lines.forEachIndexed { i, l -> c.drawText(l, M, y, if (i < lines.size - (if (r.notes.isNullOrBlank()) 0 else 1)) body else muted); y += 13 }
            y += 4; c.drawLine(M, y, W - M, y, line); y += 14
        }
        c.drawText("This list doesn't replace your prescription. Check doses with your doctor or pharmacist.", M, H - M / 2, muted)
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    fun share(context: Context, meds: List<MedEntity>, course: DoseSchedule, today: LocalDate) {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "medications.pdf")
            write(file, rows(meds, course, today), today)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "My medications")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share medication list").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            android.widget.Toast.makeText(context, "Couldn't make the list: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
