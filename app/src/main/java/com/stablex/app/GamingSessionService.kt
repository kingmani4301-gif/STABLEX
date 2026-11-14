package com.stablex.app

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class GamingSessionService : Service() {
    companion object {
        const val START = "com.stablex.app.START"
        const val STOP = "com.stablex.app.STOP"
        private const val CHANNEL = "gaming_session"
        val active = MutableStateFlow(false)
        val thermal = MutableStateFlow("Unknown")
        val thermalProtection = MutableStateFlow(false)

        /** Only restore an interruption filter that STABLEX actually changed. */
        fun recoverIfNeeded(context: Context): String? {
            val prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("pending", false)) return null
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.isNotificationPolicyAccessGranted) return "DND access revoked; check notification settings."
            val original = prefs.getInt("original", NotificationManager.INTERRUPTION_FILTER_ALL)
            return try {
                if (Build.VERSION.SDK_INT >= 35) {
                    // API 35+ manages app-owned automatic zen rules for this request.
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                } else if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(original)
                }
                prefs.edit().remove("pending").remove("original").commit()
                "Recovered previous STABLEX interruption settings."
            } catch (_: Exception) { "DND restoration could not complete; check settings." }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var pm: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null
    private var recovery: Runnable? = null
    private var started = false
    private var requested = GamingProfile.PERFORMANCE
    private var sessionStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "STABLEX active session",
            NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> { stopSelf(); return START_NOT_STICKY }
            START -> begin(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        if (started) return
        started = true
        sessionStart = SystemClock.elapsedRealtime()
        requested = GamingProfile.entries.firstOrNull { it.name == intent.getStringExtra("profile") }
            ?: GamingProfile.PERFORMANCE
        try {
            startForeground(40, notification(), if (Build.VERSION.SDK_INT >= 34)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: Exception) { stopSelf(); return }
        active.value = true
        pm = getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= 29) {
            listener = PowerManager.OnThermalStatusChangedListener { status ->
                thermal.value = thermalName(status)
                if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                    recovery?.let(handler::removeCallbacks)
                    recovery = null
                    thermalProtection.value = true
                } else if (thermalProtection.value) {
                    recovery?.let(handler::removeCallbacks)
                    recovery = Runnable {
                        thermalProtection.value = false
                        updateNotice()
                    }.also { handler.postDelayed(it, 90_000L) }
                }
                updateNotice()
            }
            try {
                pm?.addThermalStatusListener(mainExecutor, listener!!)
                thermal.value = thermalName(pm!!.currentThermalStatus)
                thermalProtection.value = pm!!.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            } catch (_: Exception) { thermal.value = "Unavailable" }
        }
        if (intent.getBooleanExtra("dnd", false)) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                val prefs = getSharedPreferences("journal", MODE_PRIVATE)
                val old = nm.currentInterruptionFilter
                if (old != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    if (prefs.edit().putInt("original", old).putBoolean("pending", true).commit()) {
                        try { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
                        catch (_: Exception) { prefs.edit().remove("pending").commit() }
                    }
                }
            }
        }
        updateNotice()
    }

    private fun thermalName(status: Int) = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown risk"
        else -> "Unknown"
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 10,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 11, Intent(this, GamingSessionService::class.java)
            .setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val label = if (thermalProtection.value) "THERMAL PROTECTION" else requested.label
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("STABLEX · Gaming session")
            .setContentText("$label · Thermal: ${thermal.value}")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, "STOP & RESTORE", stop).build()
    }

    private fun updateNotice() {
        if (!started) return
        try { getSystemService(NotificationManager::class.java).notify(40, notification()) }
        catch (_: Exception) {}
    }

    override fun onDestroy() {
        started = false
        recovery?.let(handler::removeCallbacks)
        listener?.let { thermalListener ->
            try { pm?.removeThermalStatusListener(thermalListener) } catch (_: Exception) {}
        }
        recoverIfNeeded(this)
        getSharedPreferences("journal", MODE_PRIVATE).edit()
            .putLong("last_duration_ms", SystemClock.elapsedRealtime() - sessionStart).apply()
        active.value = false
        thermalProtection.value = false
        super.onDestroy()
    }
}
