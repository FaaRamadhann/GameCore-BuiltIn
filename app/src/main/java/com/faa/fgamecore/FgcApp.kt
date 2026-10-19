package com.faa.fgamecore

import android.app.Application
import com.faa.fgamecore.core.Fgc
import com.faa.fgamecore.core.Notifier
import com.faa.fgamecore.core.ShizukuBridge
import rikka.shizuku.Shizuku

class FgcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fgc.init(this)
        Notifier.ensureChannels(this)

        Shizuku.addBinderReceivedListenerSticky { Fgc.refresh() }
        Shizuku.addBinderDeadListener { Fgc.refresh() }
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuBridge.REQ_PERMISSION) Fgc.refresh()
        }
    }
}
