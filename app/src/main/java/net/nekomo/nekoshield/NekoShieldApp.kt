package net.nekomo.nekoshield

import android.app.Application

class NekoShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Notification channel is created lazily by the service itself.
    }
}
