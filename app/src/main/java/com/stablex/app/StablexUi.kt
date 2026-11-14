package com.stablex.app

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ink = Color(0xFFF5F7FA)
private val sub = Color(0xFFBAC4D1)
private val dim = Color(0xFF9BA7B9)
private val base = Color(0xFF090C11)
private val panel = Color(0xFF151A22)
private val panelLight = Color(0xFF1C2330)
private val outline = Color(0xFF30394A)
private val blue = Color(0xFFA6CBFF)
private val positive = Color(0xFF91E0C4)
private val alert = Color(0xFFFFD497)

private val palette = darkColorScheme(
    primary = blue,
    onPrimary = Color(0xFF09111E),
    primaryContainer = Color(0xFF263C57),
    onPrimaryContainer = Color.White,
    secondary = positive,
    onSecondary = Color(0xFF10251E),
    background = base,
    onBackground = ink,
    surface = panel,
    onSurface = ink,
    surfaceVariant = panelLight,
    onSurfaceVariant = sub,
    outline = outline,
    error = Color(0xFFFFA59E),
    onError = Color(0xFF36100C)
)

@Composable
fun StablexUi(
    settings: UserSettings,
    active: Boolean,
    thermalStatus: String,
    protection: Boolean,
    installed: Boolean,
    message: String?,
    onProfile: (GamingProfile) -> Unit,
    onGame: (TargetGame) -> Unit,
    onDnd: (Boolean) -> Unit,
    onLaunch: () -> Unit,
    onPermission: (String) -> Unit
) {
    var page by remember { mutableStateOf(0) }
    MaterialTheme(colorScheme = palette) {
        Box(Modifier.fillMaxSize().background(base)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(23.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(55.dp).background(Brush.linearGradient(listOf(
                        Color(0xFF283F5B), Color(0xFF111C2B))), RoundedCornerShape(19.dp)),
                        contentAlignment = Alignment.Center) {
                        Text("S", color = ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text("STABLEX", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("PRECISION  /  STABILITY  /  PERFORMANCE", color = sub, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }

                val tabs = listOf("HOME", "PROFILES", "STATUS", "SETUP")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEachIndexed { index, item ->
                        Box(Modifier.weight(1f)
                            .background(if (page == index) panelLight else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { page = index }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Text(item, color = if (page == index) ink else sub,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp, maxLines = 1)
                        }
                    }
                }

                if (message != null) {
                    Tile {
                        Text("SYSTEM NOTICE", color = alert, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(message, color = ink, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }

                when (page) {
                    0 -> {
                        Text("The gaming session,\nunder control.", fontSize = 30.sp, lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold, color = ink)
                        Tile(highlight = true) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Kicker("ACTIVE POLICY")
                                    Text(if (protection) "Thermal protection" else
                                        settings.profile.label.lowercase().replaceFirstChar { it.uppercase() },
                                        color = ink, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                                }
                                StatusPill(if (active) "LIVE" else "READY",
                                    if (active) positive else blue)
                            }
                            HorizontalDivider(color = outline)
                            Text(if (protection) "Android reports increased thermal pressure. STABLEX is limiting its own optional workload."
                                else "Permitted system controls only. STABLEX does not alter game mechanics.",
                                color = sub, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        Kicker("CHOOSE GAME")
                        TargetGame.entries.forEach { game ->
                            Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(17.dp))
                                .clickable(enabled = !active) { onGame(game) }
                                .padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = settings.game == game, enabled = !active,
                                    onClick = { onGame(game) }, colors = RadioButtonDefaults.colors(
                                        selectedColor = blue, unselectedColor = sub))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(game.label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(if (game == settings.game && installed) "Installed · selected"
                                        else if (game == settings.game) "Not installed"
                                        else "Tap to select", color = sub, fontSize = 11.sp)
                                }
                            }
                        }
                        Button(onClick = onLaunch, modifier = Modifier.fillMaxWidth().height(62.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = blue, contentColor = Color(0xFF06101F)),
                            shape = RoundedCornerShape(19.dp)) {
                            Text(if (active) "STOP & RESTORE" else "START GAMING",
                                color = Color(0xFF06101F), fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("THERMAL · ${thermalStatus.uppercase()}",
                                if (protection) alert else positive)
                        }
                        Text("Actual BGMI/PUBG FPS is not accessible through ordinary Android app permissions.",
                            color = dim, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                    1 -> {
                        PageTitle("Performance profiles", "Three policies. No artificial CPU or GPU boosting.")
                        GamingProfile.entries.forEach { p ->
                            Tile {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text(p.label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(p.note, color = sub, fontSize = 12.sp, lineHeight = 18.sp)
                                    }
                                    RadioButton(selected = settings.profile == p, onClick = { onProfile(p) },
                                        colors = RadioButtonDefaults.colors(selectedColor = blue,
                                            unselectedColor = sub))
                                }
                            }
                        }
                        Text("These policies change STABLEX's own session behavior and monitoring; they cannot force game rendering or prevent Android thermal throttling.",
                            color = dim, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                    2 -> {
                        PageTitle("System status", "Offline information available to this app.")
                        Tile {
                            Metric("DEVICE", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                            Metric("ANDROID", "${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}")
                            Metric("THERMAL", thermalStatus)
                            Metric("SESSION", if (active) "Active" else "Idle")
                        }
                        Text("Connection status and display refresh are read on demand. No game FPS or remote ping is measured.",
                            color = dim, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    else -> {
                        PageTitle("Permissions & privacy", "Control only the Android permissions you choose.")
                        Tile {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Gaming Do Not Disturb", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text("Optional interruption policy changes", color = sub, fontSize = 12.sp)
                                }
                                Switch(checked = settings.dnd, onCheckedChange = onDnd)
                            }
                            Button(onClick = { onPermission(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
                                colors = ButtonDefaults.buttonColors(containerColor = panelLight, contentColor = ink),
                                shape = RoundedCornerShape(14.dp)) { Text("Open DND access settings", color = ink) }
                        }
                        Tile {
                            Kicker("OFFLINE BY DESIGN")
                            Text("No account. No advertising. No tracking. No internet permission.",
                                color = ink, fontSize = 14.sp, lineHeight = 21.sp)
                            Text("Automatic background game detection, overlay, and actual game-FPS reporting are not available in this version.",
                                color = sub, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable private fun Tile(highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()
        .background(if (highlight) panelLight else panel, RoundedCornerShape(21.dp))
        .then(if (highlight) Modifier.border(BorderStroke(1.dp, outline), RoundedCornerShape(21.dp)) else Modifier)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp), content = content)
}
@Composable private fun Kicker(value: String) {
    Text(value, color = sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}
@Composable private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 33.sp)
        Text(subtitle, color = sub, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
@Composable private fun StatusPill(label: String, tint: Color) {
    Box(Modifier.background(tint.copy(alpha = 0.12f), RoundedCornerShape(50.dp))
        .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
        .padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
@Composable private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = sub, fontSize = 11.sp, modifier = Modifier.weight(0.42f))
        Text(value, color = ink, fontSize = 13.sp, textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
