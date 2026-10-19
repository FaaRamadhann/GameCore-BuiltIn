package com.faa.fgamecore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.faa.fgamecore.core.Fgc
import com.faa.fgamecore.ui.FgcRoot
import com.faa.fgamecore.ui.FgcTheme

class MainActivity : ComponentActivity() {
    // Bumped on every overlay LOGS request so a running instance also navigates.
    private var logsRequest by mutableIntStateOf(
        if (intent?.getBooleanExtra(EXTRA_OPEN_LOGS, false) == true) 1 else 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FgcTheme {
                FgcRoot(openLogsRequest = logsRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_LOGS, false)) logsRequest++
    }

    override fun onResume() {
        super.onResume()
        // Shizuku state may have changed while the user was in another app.
        runCatching { Fgc.refresh() }
    }

    companion object {
        /** Set by the overlay LOGS button so the app opens on Runtime Logs. */
        const val EXTRA_OPEN_LOGS = "com.faa.fgamecore.OPEN_LOGS"
    }
}
