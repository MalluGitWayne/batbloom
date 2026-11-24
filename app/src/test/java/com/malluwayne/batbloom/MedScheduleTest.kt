package com.malluwayne.batbloom

import com.malluwayne.batbloom.data.BadgeEntity
import com.malluwayne.batbloom.data.Backup
import com.malluwayne.batbloom.data.BackupData
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.data.MedLogEntity
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.meds.MedTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MedScheduleTest {
    private val start = LocalDate.of(2026, 11, 23) // a Monday
    private fun med(freq: String, times: String = "08:00*1;20:00*2", weekdays: Int = 0b1111111, interval: Int = 2, end: String? = null, stock: Double = -1.0) =
        MedEntity(id = 1, name = "Test", form = "Tablet", frequency = freq, times = times, weekdays = weekdays, intervalDays = interval, startDate = start.toString(), endDate = end, stock = stock)

    @Test fun parseAndFormatTimes() {
        val t = MedSchedule.parseTimes("20:00*0.5; 08:00*1;bad")
        assertEquals(listOf(MedTime(LocalTime.of(8, 0), 1.0), MedTime(LocalTime.of(20, 0), 0.5)), t)
        assertEquals("08:00*1;20:00*0.5", MedSchedule.formatTimes(t))
    }

    @Test fun daily() {
        val m = med("Daily")
        assertFalse(MedSchedule.occursOn(m, start.minusDays(1)))
        assertTrue(MedSchedule.occursOn(m, start))
        val doses = MedSchedule.dosesOn(listOf(m), start)
        assertEquals(2, doses.size)
        assertEquals("2026-11-23T08:00", doses[0].slot)
        assertEquals(2.0, doses[1].qty, 0.0)
    }

    @Test fun weekdaysIntervalEndAndArchive() {
        val monWedFri = med("Weekdays", weekdays = 0b0010101)
        assertTrue(MedSchedule.occursOn(monWedFri, start))              // Mon
        assertFalse(MedSchedule.occursOn(monWedFri, start.plusDays(1))) // Tue
        assertTrue(MedSchedule.occursOn(monWedFri, start.plusDays(4)))  // Fri
        val every3 = med("Interval", interval = 3)
        assertTrue(MedSchedule.occursOn(every3, start.plusDays(3)))
        assertFalse(MedSchedule.occursOn(every3, start.plusDays(4)))
        val ending = med("Daily", end = start.plusDays(2).toString())
        assertTrue(MedSchedule.occursOn(ending, start.plusDays(2)))
        assertFalse(MedSchedule.occursOn(ending, start.plusDays(3)))
        assertFalse(MedSchedule.occursOn(med("Daily").copy(archived = true), start))
        assertFalse(MedSchedule.occursOn(med("AsNeeded"), start))
    }

    @Test fun nextTimeAndLabels() {
        val m = med("Daily")
        assertEquals(start.atTime(20, 0), MedSchedule.nextTime(listOf(m), start.atTime(9, 0)))
        assertEquals(start.plusDays(1).atTime(8, 0), MedSchedule.nextTime(listOf(m), start.atTime(20, 0)))
        assertNull(MedSchedule.nextTime(listOf(med("AsNeeded")), start.atTime(9, 0)))
        assertEquals("Mon, Wed, Fri", MedSchedule.weekdayLabel(0b0010101))
        assertEquals("Weekdays", MedSchedule.weekdayLabel(0b0011111))
        assertEquals("2 tablets", MedSchedule.qtyLabel(m, 2.0))
        assertEquals("1 tablet", MedSchedule.qtyLabel(m, 1.0))
        assertEquals("0.5 tablets", MedSchedule.qtyLabel(m, 0.5))
    }

    @Test fun stockDays() {
        val m = med("Daily", stock = 30.0) // 3 a day
        assertEquals(10, MedSchedule.daysOfStock(m, start))
        assertNull(MedSchedule.daysOfStock(med("Daily"), start))
    }

    @Test fun backupKeepsMeds() {
        val m = med("Weekdays", weekdays = 0b0010101, end = "2026-12-31", stock = 12.5).copy(notes = "after food")
        val l = MedLogEntity(7, 1, "2026-11-23T08:00", "TAKEN", 1.0, 123)
        val asNeeded = MedLogEntity(8, 1, null, "TAKEN", 2.0, 456)
        val b = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), listOf(BadgeEntity("x", 1)), emptyMap(), listOf(m), listOf(l, asNeeded))
        val back = Backup.fromJson(Backup.toJson(b))
        assertEquals(listOf(m), back.meds)
        assertEquals(listOf(l, asNeeded), back.medLogs)
    }
}
