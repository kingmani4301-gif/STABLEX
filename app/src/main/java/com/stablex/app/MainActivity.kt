package com.stablex.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

private val Context.store by preferencesDataStore(name = "stablex")
private val PROFILE_KEY = stringPreferencesKey("profile")
private val GAME_KEY = stringPreferencesKey("game")
private val DND_KEY = booleanPreferencesKey("dnd")
private val background = Color(0xFF0A0C10)
private val surface = Color(0xFF191D25)
private val accent = Color(0xFF9BC0FA)
private val muted = Color(0xFFADB6C5)

enum class GamingProfile(val label: String, val note: String) {
    PERFORMANCE("PERFORMANCE", "Prioritize responsiveness; minimal STABLEX workload"),
    BALANCED("BALANCED", "Reduce STABLEX monitoring overhead"),
    THERMAL_PROTECTION("THERMAL PROTECTION", "Conservative session monitoring")
}
enum class TargetGame(val label: String, val pkg: String) {
    BGMI("BGMI India", "com.pubg.imobile"),
    PUBG("PUBG Mobile Global", "com.tencent.ig")
}
data class UserSettings(val profile: GamingProfile = GamingProfile.PERFORMANCE,
                        val game: TargetGame = TargetGame.BGMI,
                        val dnd: Boolean = false)
fun Context.settingsFlow(): Flow<UserSettings> = store.data.map { p ->
    UserSettings(
        GamingProfile.entries.firstOrNull { it.name == p[PROFILE_KEY] } ?: GamingProfile.PERFORMANCE,
        TargetGame.entries.firstOrNull { it.name == p[GAME_KEY] } ?: TargetGame.BGMI,
        p[DND_KEY] ?: false)
}
suspend fun Context.saveProfile(value: GamingProfile) { store.edit { it[PROFILE_KEY] = value.name } }
suspend fun Context.saveGame(value: TargetGame) { store.edit { it[GAME_KEY] = value.name } }
suspend fun Context.saveDnd(value: Boolean) { store.edit { it[DND_KEY] = value } }

fun Context.gameInstalled(target: TargetGame): Boolean = try {
    packageManager.getPackageInfo(target.pkg, 0); true
} catch (_: PackageManager.NameNotFoundException) { false }

fun Context.thermalLabel(): String {
    if (Build.VERSION.SDK_INT < 29) return "Unavailable"
    return when (getSystemService(PowerManager::class.java).currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown risk"
        else -> "Unknown"
    }
}
fun Context.networkLabel(): String = try {
    val cm = getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    when {
        caps == null -> "Disconnected"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (generation unavailable)"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Other network"
    }
} catch (_: Exception) { "Unavailable" }

class MainActivity : ComponentActivity() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var refresh by mutableIntStateOf(0)
    private var message by mutableStateOf<String?>(null)
    private val notificationRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recovered = GamingSessionService.recoverIfNeeded(this)
        if (recovered != null) message = recovered
        window.statusBarColor = android.graphics.Color.rgb(10, 12, 16)
        window.navigationBarColor = android.graphics.Color.rgb(10, 12, 16)
        setContent {
            val settings by settingsFlow().collectAsState(initial = UserSettings())
            val active by GamingSessionService.active.collectAsState()
            val thermal by GamingSessionService.thermal.collectAsState()
            val protection by GamingSessionService.thermalProtection.collectAsState()
            val update = refresh
            val installed = remember(update, settings.game) { gameInstalled(settings.game) }
            StablexUi(
                settings = settings,
                active = active,
                thermalStatus = if (active) thermal else thermalLabel(),
                protection = protection,
                installed = installed,
                message = message,
                onProfile = { selected -> scope.launch { saveProfile(selected) } },
                onGame = { selected -> scope.launch { saveGame(selected) } },
                onDnd = { enabled -> scope.launch { saveDnd(enabled) } },
                onLaunch = { 
                    if (active) {
                        startService(Intent(this@MainActivity, GamingSessionService::class.java)
                            .setAction(GamingSessionService.STOP))
                        message = "Ending session and restoring STABLEX settings"
                    } else if (!installed) {
                        message = "Install ${settings.game.label} before starting."
                    } else if (settings.dnd &&
                        !getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
                        message = "Allow Do Not Disturb policy access in Setup."
                    } else if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        message = "Allow notifications to use the session controls."
                        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        try {
                            val intent = Intent(this@MainActivity, GamingSessionService::class.java)
                                .setAction(GamingSessionService.START)
                                .putExtra("profile", settings.profile.name)
                                .putExtra("dnd", settings.dnd)
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                            val launcher = packageManager.getLaunchIntentForPackage(settings.game.pkg)
                            if (launcher != null) startActivity(launcher) else {
                                message = "The selected game could not be launched."
                                startService(Intent(this@MainActivity, GamingSessionService::class.java)
                                    .setAction(GamingSessionService.STOP))
                            }
                        } catch (e: Exception) {
                            message = "Android blocked the session: ${e.javaClass.simpleName}"
                        }
                    }
                },
                onPermission = { action ->
                    try { startActivity(Intent(action)) }
                    catch (_: Exception) { message = "This settings page is not available on this device." }
                }
            )
        }
    }
    override fun onResume() { super.onResume(); refresh++ }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

@Composable private fun Section(label: String) {
    Text(label, fontSize = 11.sp, color = muted, letterSpacing = 1.5.sp)
}
@Composable private fun Note(message: String) {
    Text(message, color = muted, fontSize = 12.sp, lineHeight = 18.sp)
}
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(surface, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
