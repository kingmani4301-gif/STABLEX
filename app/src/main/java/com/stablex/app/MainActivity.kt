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
            val protected by GamingSessionService.thermalProtection.collectAsState()
            val theme = darkColorScheme(primary = accent, onPrimary = background, background = background,
                surface = surface, onSurface = Color.White, onBackground = Color.White)
            MaterialTheme(colorScheme = theme) {
                var page by remember { mutableStateOf("Home") }
                val trigger = refresh
                val installed = remember(trigger, settings.game) { gameInstalled(settings.game) }
                Column(Modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).background(surface, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                            Text("S", fontSize = 34.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("STABLEX", fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("Precision. Stability. Performance.", fontSize = 11.sp, color = muted)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Home", "Profiles", "Status", "Settings").forEach { tab ->
                            TextButton(onClick = { page = tab }) {
                                Text(tab, color = if (page == tab) accent else muted, fontSize = 12.sp)
                            }
                        }
                    }
                    message?.let { Note(it) }
                    when (page) {
                        "Home" -> {
                            Section("CURRENT POLICY")
                            Panel {
                                Text(if (protected) "THERMAL PROTECTION" else settings.profile.label,
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(if (protected) "Thermal advisory override active" else
                                    "User-authorized session controls only", color = muted, fontSize = 12.sp)
                            }
                            Section("GAME")
                            TargetGame.entries.forEach { game ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = !active) {
                                    scope.launch { saveGame(game) }
                                }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = settings.game == game, enabled = !active,
                                        onClick = { scope.launch { saveGame(game) } })
                                    Column {
                                        Text(game.label)
                                        Text(if (gameInstalled(game)) "Installed" else "Not installed", fontSize = 11.sp, color = muted)
                                    }
                                }
                            }
                            Button(onClick = {
                                if (active) {
                                    startService(Intent(this@MainActivity, GamingSessionService::class.java)
                                        .setAction(GamingSessionService.STOP))
                                    message = "Stopping and restoring authorized settings"
                                } else if (!installed) {
                                    message = "Install ${settings.game.label} before starting."
                                } else if (settings.dnd && !getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
                                    message = "Grant Do Not Disturb access in Settings first."
                                } else {
                                    try {
                                        val i = Intent(this@MainActivity, GamingSessionService::class.java)
                                            .setAction(GamingSessionService.START)
                                            .putExtra("profile", settings.profile.name)
                                            .putExtra("dnd", settings.dnd)
                                        ContextCompat.startForegroundService(this@MainActivity, i)
                                        val launch = packageManager.getLaunchIntentForPackage(settings.game.pkg)
                                        if (launch != null) startActivity(launch)
                                        else {
                                            message = "Game launcher not available"
                                            startService(Intent(this@MainActivity, GamingSessionService::class.java)
                                                .setAction(GamingSessionService.STOP))
                                        }
                                    } catch (e: Exception) {
                                        message = "Android blocked the launch: ${e.javaClass.simpleName}"
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
                                Text(if (active) "STOP & RESTORE" else "START GAMING",
                                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                            Text("Thermal: ${if (active) thermal else thermalLabel()}",
                                color = muted, fontSize = 12.sp)
                            Note("STABLEX cannot access real game FPS, change game inputs, or force CPU/GPU clocks.")
                        }
                        "Profiles" -> {
                            Section("PERFORMANCE POLICIES")
                            GamingProfile.entries.forEach { p ->
                                Panel {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(p.label, fontWeight = FontWeight.Bold)
                                            Text(p.note, fontSize = 12.sp, color = muted)
                                        }
                                        RadioButton(selected = settings.profile == p,
                                            onClick = { scope.launch { saveProfile(p) } })
                                    }
                                }
                            }
                            Note("Profiles adjust STABLEX monitoring, not the game's rendering engine.")
                        }
                        "Status" -> {
                            Section("OFFLINE DIAGNOSTICS")
                            Panel {
                                Text("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                                Text("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                                Text("Display: ${windowManager.defaultDisplay.refreshRate.toInt()} Hz (not game FPS)")
                                Text("Thermal: ${if (active) thermal else thermalLabel()}")
                                Text("Network: ${networkLabel()}")
                                Text("Session: ${if (active) "Active" else "Inactive"}")
                            }
                            Note("No external ping, jitter, packet loss, or BGMI server measurements are possible offline.")
                        }
                        "Settings" -> {
                            Section("INTERRUPTION CONTROL")
                            Panel {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Gaming Do Not Disturb", modifier = Modifier.weight(1f))
                                    Switch(checked = settings.dnd, onCheckedChange = {
                                        scope.launch { saveDnd(it) }
                                    })
                                }
                                Text("Requires user-granted Notification Policy Access", color = muted, fontSize = 12.sp)
                                Button(onClick = {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                }) { Text("DND permission settings") }
                            }
                            Section("ABOUT")
                            Panel {
                                Text("STABLEX 0.1.0")
                                Text("Offline · No account · No ads · No internet permission", color = muted)
                                Text("Game autostart and floating sidebar are not in this verified build.",
                                    fontSize = 12.sp, color = muted)
                            }
                        }
                    }
                }
            }
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
