package com.malluwayne.batbloom

import android.app.Application
import com.malluwayne.batbloom.data.AppDatabase
import com.malluwayne.batbloom.data.Prefs
import com.malluwayne.batbloom.data.Repository
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.widget.BloomWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BatbloomApp : Application() {
    lateinit var repo: Repository private set
    lateinit var prefs: Prefs private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(AppDatabase.create(this))
        prefs = Prefs(this)
        // A fresh process cannot have a live session, so a row still RUNNING was cut off (phone restart, app killed).
        if (FocusEngine.state.value is FocusEngine.State.Idle) appScope.launch {
            repo.failStaleRunning()
            BloomWidget.refresh(this@BatbloomApp)
        }
    }

    companion object {
        lateinit var instance: BatbloomApp private set
    }
}
