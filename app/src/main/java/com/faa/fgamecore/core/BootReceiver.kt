package com.faa.fgamecore.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED
        // BOOT_COMPLETED is a protected broadcast: `am broadcast` from adb is denied
        // on Android 12+, so the debug manifest also registers a test action. Release
        // never registers it and keeps the receiver unexported, so this branch is
        // unreachable there.
        val test = intent.action == ACTION_DEBUG_BOOT
        if (!boot && !test) return
        Fgc.init(context)
        // Shizuku is usually not up yet at boot: without goAsync the process can be
        // killed before the retries run and a frozen package stays disabled.
        val pending = goAsync()
        Fgc.scope.launch {
            try {
                Fgc.bootRecovery()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DEBUG_BOOT = "com.faa.fgamecore.action.DEBUG_BOOT"
    }
}
