package com.malluwayne.batbloom

import android.app.Application
import com.malluwayne.batbloom.data.AppDatabase
import com.malluwayne.batbloom.data.Prefs
import com.malluwayne.batbloom.data.Repository
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.quit.DoseAlarms
import com.malluwayne.batbloom.widget.BloomWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BatbloomApp : Application() {
    lateinit var db: AppDatabase private set
    lateinit var repo: Repository private set
    lateinit var prefs: Prefs private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.create(this)
        repo = Repository(db)
        prefs = Prefs(this)
        // A fresh process cannot have a live session, so a row still RUNNING was cut off (phone restart, app killed).
        if (FocusEngine.state.value is FocusEngine.State.Idle) appScope.launch {
            repo.failStaleRunning()
            BloomWidget.refresh(this@BatbloomApp)
        }
        DoseAlarms.ensureChannels(this)
        DoseAlarms.scheduleNext(this)
        com.malluwayne.batbloom.guard.Reminders.ensureChannels(this)
        com.malluwayne.batbloom.guard.Reminders.scheduleAll(this)
        appScope.launch { DoseAlarms.checkBadges(this@BatbloomApp) }
        com.malluwayne.batbloom.meds.MedAlarms.ensureChannel(this)
        appScope.launch { com.malluwayne.batbloom.meds.MedAlarms.scheduleNext(this@BatbloomApp) }
    }

    /** The phone switched dark/light mode: widgets on System follow it. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        BloomWidget.refresh(this)
    }

    companion object {
        lateinit var instance: BatbloomApp private set
    }
}
