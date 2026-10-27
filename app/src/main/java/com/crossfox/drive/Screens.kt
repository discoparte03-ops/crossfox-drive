@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.crossfox.drive

import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object C {
    val bg = Color(0xFF07070A); val panel = Color(0xFF121216); val panel2 = Color(0xFF1B1B20)
    val red = Color(0xFFE11D2A); val redDark = Color(0xFF4A0D13); val border = Color(0xFF3A1217)
    val dim = Color(0xFF9A9AA3); val warn = Color(0xFFFFB020); val ok = Color(0xFF3DDC84)
}

enum class Screen { HOME, APPS, NOTIFS, SETTINGS }
enum class Dlg { OIL, TAXI, NAV, VEHICLE }

@Composable
fun CrossfoxApp(repo: SettingsRepo, listenerEnabled: Boolean, homeSignal: Int, activity: ComponentActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by repo.flow.collectAsState(initial = AppSettings())
    var screen by remember { mutableStateOf(Screen.HOME) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    val notifCount by Bridge.notifications.collectAsState()

    LaunchedEffect(homeSignal) { screen = Screen.HOME; dialog = null }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    LaunchedEffect(s.orientation) {
        activity.requestedOrientation =
            if (s.orientation == 0) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    // Modo noche: solo baja el brillo de ESTA ventana (no toca ajustes globales).
    LaunchedEffect(s.nightOn, s.nightLevel) {
        val lp = activity.window.attributes
        lp.screenBrightness = if (s.nightOn) (0.35f - 0.3f * s.nightLevel).coerceAtLeast(0.05f)
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }
    val save: ((androidx.datastore.preferences.core.MutablePreferences) -> Unit) -> Unit =
        { block -> scope.launch { repo.edit(block) } }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Sidebar(screen, notifCount.size) { screen = it }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(s, listenerEnabled, { dialog = it }, { screen = it }, save)
                    Screen.APPS -> AppsScreen(s, save)
                    Screen.NOTIFS -> NotifsScreen(listenerEnabled)
                    Screen.SETTINGS -> SettingsScreen(s, listenerEnabled, save)
                }
            }
        }
        if (s.nightOn) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f + 0.6f * s.nightLevel)))
    }

    when (dialog) {
        Dlg.OIL -> AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton({ dialog = null }) { Text("Cerrar") } },
            title = { Text("Cambio de aceite") },
            text = { MaintForm(s) { cur, last, itv ->
                save { p -> p[K.currentKm] = cur; p[K.interval] = itv; if (last >= 0) p[K.lastOilKm] = last }
                toast(ctx, "Guardado")
            } },
        )
        Dlg.TAXI -> TaxiDialog(onDismiss = { dialog = null })
        Dlg.NAV -> NavDialog(onDismiss = { dialog = null })
        Dlg.VEHICLE -> AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton({ dialog = null }) { Text("Cerrar") } },
            title = { Text("Perfil del vehículo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Volkswagen Crossfox · 2017")
                    Text("Motor 1.6 MSI · Gasolina y GLP")
                    Text("Aceite habitual: 5W-30 · cada ${fmt(s.intervalKm)} km")
                    Text("Km (manual): ${fmt(s.currentKm)}")
                    Text("Último cambio de aceite: " + if (s.lastOilKm >= 0) "${fmt(s.lastOilKm)} km" else "Sin datos")
                    Text("Combustible, temperatura, presión de neumáticos: Sin datos (requiere OBD-II, aún no implementado)", color = C.dim)
                }
            },
        )
        null -> {}
    }
}

// ------------------------------------------------------------------ componentes base
@Composable
fun Panel(modifier: Modifier = Modifier, accent: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    var m = modifier.clip(shape).background(if (accent) C.redDark.copy(alpha = 0.55f) else C.panel)
        .border(1.dp, if (accent) C.red else C.border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(14.dp), content = content)
}

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) { value = LocalDateTime.now(); delay(1000L * (60 - value.second)) }
}

@Composable
fun Sidebar(cur: Screen, badge: Int, onSel: (Screen) -> Unit) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(Color(0xFF0B0B0E)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NavItem("🏠", "Inicio", cur == Screen.HOME, 0) { onSel(Screen.HOME) }
        NavItem("▦", "Apps", cur == Screen.APPS, 0) { onSel(Screen.APPS) }
        NavItem("🔔", "Avisos", cur == Screen.NOTIFS, badge) { onSel(Screen.NOTIFS) }
        NavItem("⚙", "Ajustes", cur == Screen.SETTINGS, 0) { onSel(Screen.SETTINGS) }
        Spacer(Modifier.weight(1f))
        Text("Crossfox", fontWeight = FontWeight.Bold)
        Text("2017 | 1.6 MSI", color = C.dim, fontSize = 11.sp)
    }
}

@Composable
fun NavItem(icon: String, label: String, selected: Boolean, badge: Int, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(84.dp).background(if (selected) C.red.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 28.sp)
        Text(if (badge > 0) "$label ($badge)" else label, fontSize = 12.sp, color = if (selected) C.red else Color.White)
    }
}

@Composable
fun CarArt(modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val body = Path().apply {
        moveTo(w * 0.04f, h * 0.72f); lineTo(w * 0.06f, h * 0.52f); lineTo(w * 0.22f, h * 0.46f)
        lineTo(w * 0.34f, h * 0.20f); lineTo(w * 0.72f, h * 0.20f); lineTo(w * 0.86f, h * 0.46f)
        lineTo(w * 0.96f, h * 0.54f); lineTo(w * 0.97f, h * 0.72f); close()
    }
    drawPath(body, Color(0xFF1E1E24)); drawPath(body, C.red, style = Stroke(width = 3f))
    val win = Path().apply {
        moveTo(w * 0.26f, h * 0.46f); lineTo(w * 0.36f, h * 0.26f); lineTo(w * 0.70f, h * 0.26f)
        lineTo(w * 0.81f, h * 0.46f); close()
    }
    drawPath(win, Color(0xFF0B0B0E))
    listOf(0.24f, 0.77f).forEach { x ->
        val c = Offset(w * x, h * 0.74f)
        drawCircle(Color.Black, h * 0.15f, c); drawCircle(C.red, h * 0.15f, c, style = Stroke(3f)); drawCircle(C.dim, h * 0.06f, c)
    }
}

@Composable
fun AppIcon(pkgs: List<String>, size: Int = 56) {
    val ctx = LocalContext.current
    val pkg = remember(pkgs) { installedPkg(ctx, pkgs) }
    val bmp: ImageBitmap? = remember(pkg) {
        pkg?.let { runCatching { ctx.packageManager.getApplicationIcon(it).toBitmap(128, 128).asImageBitmap() }.getOrNull() }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size.dp))
    else Box(Modifier.size(size.dp).clip(CircleShape).background(C.panel2), contentAlignment = Alignment.Center) {
        Text("?", color = C.dim, fontSize = 22.sp)
    }
}

@Composable
fun FavTile(pkg: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val def = Catalog.byPkg(pkg)
    val pkgs = def?.pkgs ?: listOf(pkg)
    val label = labelFor(ctx, pkg)
    val installed = remember(pkg) { installedPkg(ctx, pkgs) != null }
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable { openApp(ctx, label, pkgs) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(pkgs)
        Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (installed) Color.White else C.dim)
        if (!installed) Text("No instalada", fontSize = 10.sp, color = C.warn)
    }
}

// ------------------------------------------------------------------ HOME
@Composable
fun HomeScreen(
    s: AppSettings, listenerEnabled: Boolean, onDialog: (Dlg) -> Unit, onScreen: (Screen) -> Unit,
    save: ((androidx.datastore.preferences.core.MutablePreferences) -> Unit) -> Unit,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar()
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VehiclePanel(Modifier.weight(1.1f).fillMaxHeight()) { onDialog(Dlg.VEHICLE) }
            Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.showMusic) MusicPanel(Modifier.weight(1f).fillMaxWidth(), listenerEnabled)
                Panel(Modifier.weight(1f).fillMaxWidth()) {
                    val grid = s.favorites.take(7)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val cells: List<String?> = grid + List(8 - grid.size) { null }
                        cells.chunked(4).forEach { row ->
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                row.forEachIndexed { i, pkg ->
                                    val isLast = row === cells.chunked(4).last() && i == 3
                                    if (isLast) MoreTile(Modifier.weight(1f).fillMaxHeight()) { onScreen(Screen.APPS) }
                                    else if (pkg != null) FavTile(pkg, Modifier.weight(1f).fillMaxHeight())
                                    else Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.showNav) NavPanel(Modifier.weight(1f).fillMaxWidth()) { onDialog(Dlg.NAV) }
                if (s.showMaint) MaintPanel(Modifier.weight(1f).fillMaxWidth(), s) { onDialog(Dlg.OIL) }
            }
        }
        Row(Modifier.fillMaxWidth().height(108.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1.5f).fillMaxHeight()) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("“Aun en la noche más oscura, el camino sigue.”", fontSize = 14.sp)
                        Text("Crossfox · el camino también es tuyo", fontSize = 11.sp, color = C.dim)
                    }
                    Button(
                        onClick = { save { it[K.nightOn] = !s.nightOn } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (s.nightOn) C.red else C.panel2),
                    ) { Text(if (s.nightOn) "☾ Noche: ON" else "☾ Modo noche") }
                }
            }
            Panel(Modifier.weight(1.3f).fillMaxHeight(), accent = true) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MODO TAXI", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("Waze + inDrive + YouTube Music", fontSize = 11.sp, color = C.dim)
                    }
                    Button(
                        onClick = {
                            if (s.taxiSplit) {
                                if (!launchTaxiPair(ctx)) { toast(ctx, "Waze o inDrive no están instaladas"); onDialog(Dlg.TAXI) }
                            } else onDialog(Dlg.TAXI)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = C.red),
                        modifier = Modifier.height(56.dp),
                    ) { Text("⚡ INICIAR", fontWeight = FontWeight.Bold) }
                }
            }
            Panel(Modifier.weight(1.7f).fillMaxHeight()) {
                Row(Modifier.fillMaxSize()) {
                    s.favorites.drop(7).take(5).forEach { FavTile(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
fun MoreTile(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(C.panel2), contentAlignment = Alignment.Center) { Text("+", fontSize = 30.sp) }
        Text("Más apps", fontSize = 13.sp)
    }
}

@Composable
fun TopBar() {
    val ctx = LocalContext.current
    val now by rememberNow()
    val net by produceState(netLabel(ctx)) { while (true) { value = netLabel(ctx); delay(10_000) } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.red), contentAlignment = Alignment.Center) {
            Text("CF", fontWeight = FontWeight.ExtraBold)
        }
        Column {
            Text("CROSSFOX DRIVE", fontWeight = FontWeight.Bold, letterSpacing = 4.sp, fontSize = 20.sp)
            Text("Más que un auto, un estilo de vida", fontSize = 11.sp, color = C.dim)
        }
        Box(Modifier.weight(1f).height(2.dp).background(C.red.copy(alpha = 0.7f)))
        Text(net, color = C.dim, fontSize = 14.sp)
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VehiclePanel(modifier: Modifier, onClick: () -> Unit) {
    val now by rememberNow()
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es", "PE"))).replaceFirstChar { it.uppercase() }
    Panel(modifier, onClick = onClick) {
        Column(Modifier.fillMaxSize()) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 72.sp, fontWeight = FontWeight.ExtraBold)
            Text(date, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text("Volkswagen Crossfox 2017", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("1.6 MSI · Gasolina + GLP", color = C.dim)
            Text("Huancayo, Perú", color = C.dim)
            CarArt(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp))
            Text("Toca para ver el perfil del vehículo", fontSize = 12.sp, color = C.dim)
        }
    }
}

@Composable
fun CtlBtn(glyph: String, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (big) 64.dp else 52.dp).clip(CircleShape).border(if (big) 2.dp else 1.dp, C.red, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = if (big) 28.sp else 22.sp) }
}

@Composable
fun MusicPanel(modifier: Modifier, enabled: Boolean) {
    val ctx = LocalContext.current
    val np by Bridge.nowPlaying.collectAsState()
    val cur = np
    Panel(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Música", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (enabled && cur != null) Text(if (cur.playing) "En reproducción" else "En pausa", color = C.dim, fontSize = 13.sp)
            }
            when {
                !enabled -> Text("Activa el acceso a notificaciones (Ajustes) para ver la canción actual y controlar la reproducción.", color = C.dim, fontSize = 14.sp)
                cur == null -> Text("Sin reproducción activa", color = C.dim)
                else -> Column {
                    Text(cur.title ?: "Sin título", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cur.artist ?: "", color = C.dim, maxLines = 1)
                    Text("Fuente: " + labelFor(ctx, cur.pkg), color = C.dim, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (enabled && cur != null) {
                    CtlBtn("⏮") { Bridge.controller?.transportControls?.skipToPrevious() }
                    CtlBtn(if (cur.playing) "⏸" else "▶", big = true) {
                        val t = Bridge.controller?.transportControls
                        if (cur.playing) t?.pause() else t?.play()
                    }
                    CtlBtn("⏭") { Bridge.controller?.transportControls?.skipToNext() }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { openApp(ctx, "YouTube Music", Catalog.YTMUSIC.pkgs) }, colors = ButtonDefaults.buttonColors(containerColor = C.red)) {
                    Text("Abrir YouTube Music")
                }
            }
        }
    }
}

@Composable
fun NavPanel(modifier: Modifier, onDest: () -> Unit) {
    val ctx = LocalContext.current
    Panel(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Navegación", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Abre Waze o Google Maps, o indica un destino.", color = C.dim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ openApp(ctx, "Waze", Catalog.WAZE.pkgs) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = C.panel2)) { Text("Waze") }
                Button({ openApp(ctx, "Google Maps", Catalog.MAPS.pkgs) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = C.panel2)) { Text("Maps") }
            }
            Button(onDest, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = C.red)) { Text("Ir a un destino…") }
        }
    }
}

@Composable
fun MaintPanel(modifier: Modifier, s: AppSettings, onClick: () -> Unit) {
    val st = computeOil(s)
    val color = when (st.level) { Level.DUE -> C.red; Level.SOON -> C.warn; Level.OK -> C.ok; Level.NO_DATA -> C.dim }
    Panel(modifier, onClick = onClick) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("🔧 Cambio de aceite", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { st.progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = color, trackColor = C.panel2)
            when (st.level) {
                Level.NO_DATA -> Text("Sin datos · toca para registrar el último cambio", color = C.dim)
                Level.DUE -> Text("¡Cambio vencido por ${fmt(-st.remaining)} km!", color = C.red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                else -> {
                    Text(if (st.level == Level.SOON) "⚠ Servicio próximo" else "Próximo servicio en:", color = color, fontSize = 13.sp)
                    Text("${fmt(st.remaining)} km", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text("Último: ${if (s.lastOilKm >= 0) fmt(s.lastOilKm) + " km" else "Sin datos"} · Intervalo: ${fmt(s.intervalKm)} km", color = C.dim, fontSize = 12.sp)
            Text("Km actual (manual): ${fmt(s.currentKm)}", color = C.dim, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------ Diálogos
@Composable
fun TaxiDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
        title = { Text("MODO TAXI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Catalog.WAZE, Catalog.INDRIVE, Catalog.YTMUSIC).forEach { app ->
                    Button({ openApp(ctx, app.label, app.pkgs) }, Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = C.panel2)) {
                        AppIcon(app.pkgs, 36); Spacer(Modifier.width(12.dp)); Text(app.label, fontSize = 20.sp)
                    }
                }
                Button({
                    if (!launchTaxiPair(ctx)) toast(ctx, "Waze o inDrive no están instaladas")
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = C.red)) { Text("Waze + inDrive en pantalla dividida (experimental)") }
                Text(
                    "Android solo abre la 2ª app al lado si la multiventana lo permite; si no, se abre a pantalla completa. " +
                        "Más fiable: crea un «par de apps» de Samsung (Panel Edge → Multiventana → Crear par). " +
                        "Tres ventanas a la vez no está garantizado en esta tablet.",
                    fontSize = 12.sp, color = C.dim,
                )
            }
        },
    )
}

@Composable
fun NavDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var dest by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
        title = { Text("Ir a un destino") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dest, { dest = it }, label = { Text("Destino (dirección o lugar)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({
                        if (dest.isBlank()) toast(ctx, "Escribe un destino")
                        else if (!navigateTo(ctx, dest, true)) toast(ctx, "Waze no está instalada")
                    }, Modifier.weight(1f)) { Text("Con Waze") }
                    Button({
                        if (dest.isBlank()) toast(ctx, "Escribe un destino")
                        else if (!navigateTo(ctx, dest, false)) toast(ctx, "Google Maps no está instalada")
                    }, Modifier.weight(1f)) { Text("Con Maps") }
                }
                Text("Configura el destino con el vehículo estacionado.", fontSize = 12.sp, color = C.dim)
            }
        },
    )
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(7)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
    )
}

/** onSave(kmActual, kmUltimoCambio (-1 = sin dato), intervalo) */
@Composable
fun MaintForm(s: AppSettings, onSave: (Int, Int, Int) -> Unit) {
    var cur by remember(s.currentKm) { mutableStateOf(s.currentKm.toString()) }
    var last by remember(s.lastOilKm) { mutableStateOf(if (s.lastOilKm >= 0) s.lastOilKm.toString() else "") }
    var itv by remember(s.intervalKm) { mutableStateOf(s.intervalKm.toString()) }
    var err by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField("Kilometraje actual (manual)", cur) { cur = it }
        NumField("Km del último cambio de aceite", last) { last = it }
        NumField("Intervalo (km)", itv) { itv = it }
        err?.let { Text(it, color = C.warn, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                val e = validateOil(cur.toIntOrNull(), last.toIntOrNull(), itv.toIntOrNull())
                err = e
                if (e == null) onSave(cur.toInt(), last.toIntOrNull() ?: -1, itv.toInt())
            }, Modifier.weight(1f)) { Text("Guardar") }
            Button({
                // Nuevo cambio de aceite: el último cambio pasa a ser el km actual.
                val e = validateOil(cur.toIntOrNull(), null, itv.toIntOrNull())
                err = e
                if (e == null) { last = cur; onSave(cur.toInt(), cur.toInt(), itv.toInt()) }
            }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = C.red)) { Text("Registrar cambio ahora") }
        }
    }
}

// ------------------------------------------------------------------ APPS
data class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap?)

@Composable
fun AppsScreen(s: AppSettings, save: ((androidx.datastore.preferences.core.MutablePreferences) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppEntry>()) {
        value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map {
                    AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName,
                        runCatching { it.loadIcon(pm).toBitmap(128, 128).asImageBitmap() }.getOrNull())
                }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Todas las aplicaciones", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Toca para abrir · mantén pulsado para añadir a favoritos", color = C.dim, fontSize = 13.sp)
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(apps, key = { it.pkg }) { a ->
                Column(
                    Modifier.padding(4.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(
                        onClick = { openApp(ctx, a.label, listOf(a.pkg)) },
                        onLongClick = {
                            if (a.pkg !in s.favorites) {
                                save { it[K.favorites] = (s.favorites + a.pkg).joinToString(",") }
                                toast(ctx, "«${a.label}» añadida a favoritos")
                            } else toast(ctx, "Ya está en favoritos")
                        },
                    ).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    a.icon?.let { Image(it, null, Modifier.size(56.dp)) }
                    Text(a.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ AVISOS
@Composable
fun NotifsScreen(enabled: Boolean) {
    val ctx = LocalContext.current
    val list by Bridge.notifications.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Notificaciones", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (!enabled) {
            Text("Para ver tus notificaciones aquí debes autorizar a Crossfox Drive en Ajustes de Android. Se leen solo en el dispositivo; no se guardan ni se envían a ningún servidor.", color = C.dim)
            Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Abrir ajustes de acceso") }
        } else if (list.isEmpty()) {
            Text("Sin notificaciones", color = C.dim)
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.key }) { n ->
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(n.appLabel, color = C.red, fontSize = 12.sp)
                        Text(n.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.text, color = C.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ AJUSTES
@Composable
fun SettingsScreen(
    s: AppSettings, listenerEnabled: Boolean,
    save: ((androidx.datastore.preferences.core.MutablePreferences) -> Unit) -> Unit,
) {
    val ctx = LocalContext.current
    fun favs(l: List<String>) = save { it[K.favorites] = l.joinToString(",") }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Ajustes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("⚠ Configura con el vehículo estacionado.", color = C.warn)
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sistema", fontWeight = FontWeight.Bold)
                    Button({ ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Elegir app de inicio predeterminada") }
                    Text("Acceso a notificaciones y música: " + if (listenerEnabled) "activado" else "desactivado", color = C.dim)
                    Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Abrir ajustes de acceso a notificaciones") }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mantenimiento (aceite 5W-30)", fontWeight = FontWeight.Bold)
                    MaintForm(s) { cur, last, itv ->
                        save { p -> p[K.currentKm] = cur; p[K.interval] = itv; if (last >= 0) p[K.lastOilKm] = last }
                        toast(ctx, "Guardado")
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text("Modo noche", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activado", Modifier.weight(1f)); Switch(s.nightOn, { v -> save { it[K.nightOn] = v } })
                    }
                    Text("Intensidad", color = C.dim)
                    Slider(s.nightLevel, { v -> save { it[K.nightLevel] = v } })
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text("Paneles visibles", fontWeight = FontWeight.Bold)
                    listOf(Triple("Música", s.showMusic, K.showMusic), Triple("Navegación", s.showNav, K.showNav), Triple("Mantenimiento", s.showMaint, K.showMaint)).forEach { (n, v, key) ->
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(n, Modifier.weight(1f)); Switch(v, { nv -> save { it[key] = nv } }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Orientación", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(s.orientation == 0, { save { it[K.orientation] = 0 } }, { Text("Horizontal fija") })
                        FilterChip(s.orientation == 1, { save { it[K.orientation] = 1 } }, { Text("Automática") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("MODO TAXI", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("INICIAR intenta Waze + inDrive en pantalla dividida (si no, abre el panel)", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(s.taxiSplit, { v -> save { it[K.taxiSplit] = v } })
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Favoritos y orden", fontWeight = FontWeight.Bold)
                    Text("Los 7 primeros van al panel de apps; los 5 siguientes a la franja inferior.", color = C.dim, fontSize = 12.sp)
                    s.favorites.forEachIndexed { i, pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${labelFor(ctx, pkg)}", Modifier.weight(1f))
                            TextButton({ if (i > 0) favs(s.favorites.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }) { Text("▲") }
                            TextButton({ if (i < s.favorites.size - 1) favs(s.favorites.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }) { Text("▼") }
                            TextButton({ favs(s.favorites - pkg) }) { Text("✕") }
                        }
                    }
                    val missing = Catalog.apps.map { it.pkgs.first() }.filter { it !in s.favorites }
                    if (missing.isNotEmpty()) {
                        Text("Añadir:", color = C.dim)
                        missing.forEach { p -> TextButton({ favs(s.favorites + p) }) { Text("+ ${labelFor(ctx, p)}") } }
                    }
                    Text("Para otras apps: pantalla «Apps» → mantener pulsado.", color = C.dim, fontSize = 12.sp)
                    TextButton({ favs(Catalog.defaultFavorites) }) { Text("Restablecer favoritos") }
                }
            }
        }
    }
}
