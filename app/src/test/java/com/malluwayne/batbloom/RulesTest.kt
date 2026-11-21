package com.malluwayne.batbloom

import com.malluwayne.batbloom.focus.AllowPolicy
import com.malluwayne.batbloom.focus.LeaveGuard
import com.malluwayne.batbloom.focus.PomodoroPlan
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.focus.Stage
import com.malluwayne.batbloom.focus.Verdict
import com.malluwayne.batbloom.focus.stageFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test fun minutesAreClampedTo10to120() {
        assertEquals(10, Rules.clampMinutes(3))
        assertEquals(120, Rules.clampMinutes(500))
        assertEquals(45, Rules.clampMinutes(45))
    }

    @Test fun progressAndStages() {
        assertEquals(0f, Rules.progress(1000, 2000, 500), 0f)
        assertEquals(0.5f, Rules.progress(1000, 2000, 1500), 0.0001f)
        assertEquals(1f, Rules.progress(1000, 2000, 9000), 0f)
        assertEquals(Stage.Seed, stageFor(0f))
        assertEquals(Stage.Sprout, stageFor(0.2f))
        assertEquals(Stage.Sapling, stageFor(0.5f))
        assertEquals(Stage.Bud, stageFor(0.99f))
        assertEquals(Stage.Bloom, stageFor(1f))
    }

    @Test fun unlocking() {
        assertTrue(Rules.canUnlock(150, 150, false))
        assertFalse(Rules.canUnlock(149, 150, false))
        assertFalse(Rules.canUnlock(999, 150, true))
    }

    @Test fun formatMinutes() {
        assertEquals("25m", Rules.formatMinutes(25))
        assertEquals("1h 05m", Rules.formatMinutes(65))
        assertEquals("0m", Rules.formatMinutes(0))
    }

    @Test fun allowPolicy() {
        val p = AllowPolicy("me", setOf("launcher"), setOf("dictionary"))
        assertTrue(p.isAllowed("me"))
        assertTrue(p.isAllowed("launcher"))
        assertTrue(p.isAllowed("dictionary"))
        assertFalse(p.isAllowed("instagram"))
    }

    @Test fun leavingStartsGraceThenFails() {
        val g = LeaveGuard(10_000)
        assertEquals(Verdict.Ok, g.update("me", true, true, 0))
        assertEquals(Verdict.Warning(10, "insta"), g.update("insta", false, true, 1_000))
        assertEquals(Verdict.Warning(5, "insta"), g.update("insta", false, true, 6_000))
        assertEquals(Verdict.Fail("insta"), g.update("insta", false, true, 11_000))
    }

    @Test fun comingBackInTimeResetsGrace() {
        val g = LeaveGuard(10_000)
        g.update("insta", false, true, 0)
        assertEquals(Verdict.Ok, g.update("me", true, true, 9_000))
        // A fresh grace period on the next slip.
        assertEquals(Verdict.Warning(10, "insta"), g.update("insta", false, true, 20_000))
    }

    @Test fun screenOffNeverFails() {
        val g = LeaveGuard(10_000)
        g.update("insta", false, true, 0)
        assertEquals(Verdict.Ok, g.update("insta", false, false, 60_000))
        assertEquals(Verdict.Ok, g.update(null, false, true, 70_000))
    }

    @Test fun pomodoroPlan() {
        val p = PomodoroPlan(focusMinutes = 25, shortBreak = 5, longBreak = 15, rounds = 8, longEvery = 4)
        assertEquals(5, p.breakAfter(1))
        assertEquals(15, p.breakAfter(4))
        assertTrue(p.isLongBreak(8))
        assertFalse(p.isLast(7))
        assertTrue(p.isLast(8))
        // 8×25 focus + breaks after rounds 1..7: six short (5) and one long (15)
        assertEquals(200 + 30 + 15, p.totalMinutes())
        assertEquals(25 * 4 + 5 * 3, PomodoroPlan().totalMinutes())
    }
}
