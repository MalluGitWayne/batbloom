@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.malluwayne.batbloom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.meds.Frequency
import com.malluwayne.batbloom.meds.MedForm
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.meds.Meds
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
internal val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** The varenicline course shown alongside the user's own medications. */
internal val VareniclineArt = MedEntity(name = "Varenicline", form = "Tablet", shape = "Oval", color1 = 0xFFFFFFFF, color2 = 0xFFFFFFFF, background = 0xFF203A43, startDate = "2000-01-01")

@Composable
fun MedsScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val dao = app.db.meds()
    val zone = ZoneId.systemDefault()
    val nowMs = rememberNow(30_000)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val today = now.toLocalDate()
    val meds by dao.all().collectAsState(emptyList())
    val since = remember(today) { today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli() }
    val logs by remember(today) { dao.logsSince(since, today.minusDays(6).toString()) }.collectAsState(emptyList())
    val schedule by app.prefs.schedule.collectAsState()
    val courseLogs by app.repo.doses.collectAsState(emptyList())

    var editing by remember { mutableStateOf<MedEntity?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var courseDetail by remember { mutableStateOf(false) }

    editing?.let { m ->
        BackHandler { editing = null }
        MedEditor(m, modifier, onClose = { editing = null })
        return
    }
    if (courseDetail) {
        BackHandler { courseDetail = false }
        CourseDetail(modifier) { courseDetail = false }
        return
    }
    detailId?.let { id ->
        val m = meds.firstOrNull { it.id == id }
        if (m != null) {
            BackHandler { detailId = null }
            MedDetail(m, modifier, onBack = { detailId = null }, onEdit = { editing = m })
            return
        }
    }

    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var askLog by remember { mutableStateOf<MedEntity?>(null) }
    var sheet by remember { mutableStateOf<List<String>?>(null) }
    var reminderSettings by remember { mutableStateOf(false) }

    val logBySlot = logs.filter { it.slot != null }.associateBy { it.medId to it.slot }
    val courseByKey = courseLogs.associateBy { it.key }
    fun itemsOn(d: LocalDate): List<DoseItem> = buildList {
        MedSchedule.dosesOn(meds, d).forEach { add(DoseItem.Med(it, logBySlot[it.med.id to it.slot])) }
        schedule.slotsOn(d).forEach { s -> add(DoseItem.Course(s, schedule.timeOf(s, zone).toLocalDateTime(), courseByKey[s.key])) }
    }.sortedBy { it.at }
    // Recomputed only when the data or the day changes, not on every clock tick.
    val items = remember(meds, logs, courseLogs, schedule, today) { itemsOn(today) }
    val yesterdayOpen = remember(meds, logs, courseLogs, schedule, today) { itemsOn(today.minusDays(1)).filter { it.open } }
    val allItems = (yesterdayOpen + items).associateBy { it.key }
    val week = remember(meds, logs, courseLogs, schedule, today) {
        (6 downTo 0).map { today.minusDays(it.toLong()) }.map { d -> itemsOn(d).let { l -> DayDoses(d, l.size, l.count { it.status == DoseStatus.TAKEN }) } }
    }
    val asNeeded = meds.filter { Frequency.of(it.frequency) == Frequency.AsNeeded && MedSchedule.isActive(it, today) }
    val active = meds.filter { !it.archived }
    val archived = meds.filter { it.archived }
    val courseOn = schedule.dayNumber(today) in 1..schedule.courseDays

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Medications", style = MaterialTheme.typography.headlineMedium)
                Text(dayFmt.format(today), color = Muted, fontSize = 13.sp)
            }
            androidx.compose.material3.IconButton({ editing = newMed(today) }) {
                androidx.compose.material3.Icon(Icons.Filled.Add, "Add a medication")
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                androidx.compose.material3.IconButton({ menu = true }) {
                    androidx.compose.material3.Icon(Icons.Filled.MoreVert, "More")
                }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem({ Text("Reminders") }, { menu = false; reminderSettings = true })
                    androidx.compose.material3.DropdownMenuItem({ Text("Share medication list (PDF)") }, {
                        menu = false; MedListPdf.share(ctx, meds.filter { !it.archived }, schedule, today)
                    })
                }
            }
        }

        // Today's progress and the week, like the Health app's summary
        if (items.isNotEmpty() || week.any { it.due > 0 }) {
            val done = items.count { it.status == DoseStatus.TAKEN }
            val logged = items.count { !it.open }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (items.isEmpty()) "Nothing due today" else "$done of ${items.size} taken", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val next = items.firstOrNull { it.open && it.at.isAfter(now.toLocalDateTime()) }
                        val due = week.filter { it.date != today }.sumOf { it.due }
                        val took = week.filter { it.date != today }.sumOf { it.taken.coerceAtMost(it.due) }
                        Text(
                            when {
                                items.isNotEmpty() && logged == items.size -> "All logged for today"
                                next != null -> "Next at ${timeFmt.format(next.at)}"
                                items.isNotEmpty() -> "${items.size - logged} still to log"
                                due > 0 -> "${took * 100 / due}% taken in the last 6 days"
                                else -> ""
                            },
                            color = Muted, fontSize = 13.sp,
                        )
                    }
                    if (items.isNotEmpty()) androidx.compose.material3.CircularProgressIndicator(
                        { done.toFloat() / items.size },
                        Modifier.size(44.dp), color = Teal, strokeWidth = 5.dp,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), gapSize = 0.dp,
                    )
                }
                WeekDots(week, today, Modifier.padding(top = 4.dp))
            }
        }

        if (items.isEmpty() && asNeeded.isEmpty() && active.isEmpty() && !courseOn) {
            SectionCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PillArt(MedForm.Capsule, "Round", Color(0xFFFF8FB1), Color(0xFFFFFFFF), Color(0xFF2E3360), 96.dp)
                    Text("Track your medications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "Add the medicines, vitamins and supplements you take. Batbloom reminds you on time and keeps a log.",
                        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    Button({ editing = newMed(today) }, Modifier.padding(top = 12.dp)) { Text("Add a medication") }
                }
            }
        }

        if (yesterdayOpen.isNotEmpty()) {
            Text("Yesterday · not logged", style = MaterialTheme.typography.titleMedium, color = Gold)
            yesterdayOpen.groupBy { it.at }.forEach { (at, group) -> TimeGroup(at, group, now.toLocalDateTime(), true) { sheet = group.map { it.key } } }
        }

        if (items.isNotEmpty()) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            items.groupBy { it.at }.forEach { (at, group) -> TimeGroup(at, group, now.toLocalDateTime(), false) { sheet = group.map { it.key } } }
        }

        // As needed: log whenever you take one
        if (asNeeded.isNotEmpty()) {
            SectionCard("As needed") {
                asNeeded.forEach { m ->
                    val todays = logs.filter { it.medId == m.id && it.slot == null && Instant.ofEpochMilli(it.actedAt).atZone(zone).toLocalDate() == today }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillArt(m, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (todays.isEmpty()) (MedSchedule.strengthLabel(m).ifBlank { "Not taken today" })
                                else "${todays.size}× today · last ${timeFmt.format(Instant.ofEpochMilli(todays.maxOf { it.actedAt }).atZone(zone))} (${sinceLabel(nowMs - todays.maxOf { it.actedAt })})",
                                color = Muted, fontSize = 13.sp,
                            )
                        }
                        Button({ askLog = m }) { Text("Log") }
                    }
                }
            }
        }

        // Your medications
        SectionCard("Your medications") {
            if (courseOn || courseLogs.isNotEmpty()) MedListRow(
                art = { PillArt(VareniclineArt, 48.dp) },
                title = "Varenicline",
                subtitle = if (courseOn) "Quit-smoking course · day ${schedule.dayNumber(today)} of ${schedule.courseDays}" else "Quit-smoking course",
                onClick = { courseDetail = true },
            )
            active.forEach { m ->
                val days = MedSchedule.daysOfStock(m, today)
                MedListRow(
                    art = { PillArt(m, 48.dp) },
                    title = m.name + MedSchedule.strengthLabel(m).let { if (it.isBlank()) "" else " · $it" },
                    subtitle = if (MedSchedule.finished(m, today)) "Course finished \u00B7 tap to archive" else MedSchedule.summary(m) + when {
                        m.stock < 0 -> ""
                        days != null && days <= 7 -> " · only ${MedSchedule.fmtQty(m.stock)} left"
                        days != null -> " · ${MedSchedule.fmtQty(m.stock)} left, until ${DateTimeFormatter.ofPattern("d MMM").format(MedSchedule.runsOutOn(m, today))}"
                        else -> " · ${MedSchedule.fmtQty(m.stock)} left"
                    },
                    warn = days != null && days <= 7,
                    onClick = { detailId = m.id },
                )
            }
            if (active.isEmpty() && !courseOn) TextButton({ editing = newMed(today) }) { Text("+ Add a medication") }
        }
        if (archived.isNotEmpty()) SectionCard("Archived") {
            archived.forEach { m -> MedListRow({ PillArt(m, 40.dp) }, m.name, "Archived", onClick = { detailId = m.id }) }
        }
        Text(
            "Batbloom doesn't check for interactions between medicines: ask your doctor or pharmacist.",
            color = Muted, fontSize = 11.sp, lineHeight = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
    }

    sheet?.let { keys ->
        val group = keys.mapNotNull { allItems[it] }
        if (group.isEmpty()) sheet = null else LogSheet(group, now) { sheet = null }
    }
    askLog?.let { m -> LogAsNeededDialog(m, onDismiss = { askLog = null }) { qty -> BatbloomApp.instance.appScope.launch { Meds.logAsNeeded(ctx, m, qty) }; askLog = null } }
    if (reminderSettings) ReminderSettingsDialog { reminderSettings = false }
}

/** "3h 10m ago", "25m ago". */
fun sinceLabel(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 48 * 60 -> "${m / 60}h ${m % 60}m ago"
        else -> "${m / 1440} days ago"
    }
}

fun newMed(today: LocalDate) = MedEntity(name = "", form = MedForm.Tablet.name, startDate = today.toString(), times = "08:00*1")
