package io.roadsignal.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.roadsignal.cmt.CmtIntegrationInfo
import io.roadsignal.core.*
import io.roadsignal.sdk.*
import java.util.Locale

private val Ink = Color(0xFF142D3B)
private val Paper = Color(0xFFF4F5EF)
private val Lime = Color(0xFFBCE877)
private val Teal = Color(0xFF237A70)
private val Muted = Color(0xFF5F716F)
private val Orange = Color(0xFFF9A56C)
private val Line = Color(0xFFDCE1D8)

private fun Double.f(digits: Int = 1) = String.format(Locale.US, "%.${digits}f", this)

@Composable
fun RoadSignalApp(vm: LabViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Overview") }
    var welcome by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var labSection by rememberSaveable { mutableStateOf("Power") }
    val journeyList = rememberLazyListState()
    LaunchedEffect(tab, selected, labSection) { journeyList.scrollToItem(0) }
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Ink,
                onPrimary = Color.White,
                secondary = Teal,
                background = Paper,
                surface = Paper,
                onSurface = Ink,
                outline = Line,
            ),
    ) {
        if (welcome) Welcome { welcome = false }
        else
            Scaffold(
                containerColor = Paper,
                bottomBar = {
                    NavigationBar(containerColor = Paper) {
                        listOf(
                                "Overview" to Icons.Outlined.Route,
                                "Trips" to Icons.Outlined.Map,
                                "Lab" to Icons.Outlined.Science,
                                "Partners" to Icons.Outlined.Hub,
                                "Controls" to Icons.Outlined.Tune,
                            )
                            .forEach { (title, icon) ->
                                NavigationBarItem(
                                    selected = tab == title,
                                    onClick = {
                                        tab = title
                                        selected = null
                                    },
                                    icon = { Icon(icon, null) },
                                    label = { Text(title, fontSize = 10.sp) },
                                )
                            }
                    }
                },
            ) { padding ->
                val detail = s.trips.firstOrNull { it.id == selected }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp),
                    state = journeyList,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
                ) {
                    item { Brand() }
                    if (detail != null) {
                        item {
                            TextButton(onClick = { selected = null }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                                Text("Back to trips")
                            }
                        }
                        item { TripHeader(detail) }
                        item { RouteSketch(detail.route, detail.source == SourceMode.REPLAY) }
                        item {
                            Section(
                                "Who was driving?",
                                "The SDK leaves this unknown. Your correction is explicit.",
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                TripRole.entries.forEach { role ->
                                    FilterChip(
                                        selected = detail.role == role,
                                        onClick = { vm.classify(detail.id, role) },
                                        label = {
                                            Text(
                                                role.name.lowercase().replaceFirstChar {
                                                    it.uppercase()
                                                },
                                                fontSize = 11.sp,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        item {
                            Section(
                                "Event timeline",
                                "Heuristics and fixtures, not validated insurance or emergency models.",
                            )
                        }
                        if (detail.events.isEmpty())
                            item { Note("No threshold events detected in this trip.") }
                        items(detail.events) { event ->
                            Panel {
                                Text(
                                    event.kind.name.replace('_', ' '),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "+${((event.elapsedMs-detail.startElapsedMs)/1000)} s · magnitude ${event.magnitude.f()}",
                                    color = Muted,
                                )
                            }
                        }
                        item {
                            Note(
                                "End reason: ${detail.endReason.name}. Distance integrates valid speed samples after detection; it is not a calibrated odometer.",
                            )
                        }
                    } else
                        when (tab) {
                            "Overview" -> {
                                item {
                                    Heading(
                                        "A clearer view\nof every drive.",
                                        "TELEMATICS, MADE VISIBLE",
                                    )
                                }
                                item {
                                    Hero(s) {
                                        if (s.consent) vm.replay(ReplayScenario.COMMUTE)
                                        else tab = "Controls"
                                    }
                                }
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Metric(
                                            "TRIPS",
                                            s.trips.size.toString(),
                                            Modifier.weight(1f),
                                        )
                                        Metric(
                                            "QUEUED",
                                            s.pendingUploads.toString(),
                                            Modifier.weight(1f),
                                        )
                                        Metric(
                                            "LOCAL ACKS",
                                            s.localReceipts.toString(),
                                            Modifier.weight(1f),
                                        )
                                    }
                                }
                                if (!s.consent) item { ConsentCard { tab = "Controls" } }
                                item {
                                    Section(
                                        "Choose a test drive",
                                        "Repeatable synthetic journeys. No location permission needed.",
                                    )
                                }
                                items(ReplayScenario.entries) { scenario ->
                                    ScenarioCard(scenario, s.consent) { vm.replay(scenario) }
                                }
                                item { EngineCard(s) }
                            }
                            "Trips" -> {
                                item {
                                    Heading("Every trip tells\na different story.", "TRIP JOURNAL")
                                }
                                item {
                                    Note(
                                        "Replay trips are synthetic. Device sessions are marked DEVICE. Coaching scores illustrate an event pipeline; they do not estimate insurance risk.",
                                    )
                                }
                                if (s.trips.isEmpty())
                                    item {
                                        Empty(
                                            "Your journal starts here",
                                            "Run City commute from Overview to see a persisted trip and its events.",
                                        )
                                    }
                                items(s.trips) { trip -> TripCard(trip) { selected = trip.id } }
                            }
                            "Lab" -> {
                                labContent(s, vm, labSection) { labSection = it }
                            }
                            "Partners" -> {
                                item {
                                    Heading("One host.\nA clear boundary.", "CMT INTEGRATION STUDY")
                                }
                                item {
                                    Panel(color = Ink) {
                                        Tag("MOCK INTEGRATION", Lime)
                                        Text(
                                            "Cambridge Mobile Telematics",
                                            color = Color.White,
                                            fontSize = 23.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(CmtIntegrationInfo.MODE, color = Lime)
                                        Text(
                                            "This adapter uses RoadSignal fixtures. No CMT binary, account, network request or proprietary algorithm is included.",
                                            color = Color.White.copy(alpha = .8f),
                                        )
                                    }
                                }
                                item {
                                    Section(
                                        "Public capability map",
                                        "CMT describes automatic recording, transport and role classification, risky-event insights, trip details, and App+Tag programs.",
                                    )
                                }
                                item {
                                    Panel {
                                        Text("DriveWell & MobileAPI", fontWeight = FontWeight.Bold)
                                        Text(
                                            "Two SDK families discussed in CMT’s wake-lock article. Their actual APIs require partner integration material.",
                                            color = Muted,
                                        )
                                    }
                                }
                                item {
                                    Button(
                                        onClick = vm::mockPartner,
                                        enabled = s.consent,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Try mock integration")
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = vm::unavailablePartner,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Check real SDK setup")
                                    }
                                }
                                item { Note(CmtIntegrationInfo.ACCESS_NOTE) }
                                item {
                                    Note(
                                        "Our PartnerTelematicsPort is an original adapter contract. It does not claim binary or API compatibility with DriveWell or MobileAPI.",
                                    )
                                }
                            }
                            "Controls" -> {
                                item {
                                    Heading(
                                        "Your data.\nYour decision.",
                                        "CONSENT & DEVICE CAPTURE",
                                    )
                                }
                                item {
                                    Panel {
                                        Tag(
                                            if (s.consent) "CONSENT ENABLED"
                                            else "COLLECTION DISABLED",
                                            if (s.consent) Lime else Orange,
                                        )
                                        Text(
                                            "Local learning environment",
                                            fontSize = 23.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Replay creates synthetic trips. Live mode reads device location, provider speed, linear acceleration and gyroscope only after a separate start action. Trip payloads stay encrypted on this device; sync uses a local mock. No CMT connection.",
                                            color = Muted,
                                        )
                                        if (!s.consent)
                                            Button(
                                                onClick = vm::consent,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text("Enable local SDK")
                                            }
                                    }
                                }
                                item { DeviceControls(s, vm) }
                                item { EngineCard(s) }
                                item { DeleteControl(s, vm) }
                                item {
                                    Note(
                                        "No background-location request or capture auto-start at boot. WorkManager adds its own rescheduling components and may use locks. Recording has no explicit SDK-owned wake lock.",
                                    )
                                }
                            }
                        }
                    if (message.isNotBlank()) item { Note(message) }
                    item {
                        Text(
                            "ORIGINAL ENGINEERING SAMPLE • ${RoadSignal.VERSION}\nIndependent SDK example. Implementation and limits are documented.",
                            fontSize = 10.sp,
                            color = Muted,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
    }
}

@Composable
private fun Welcome(done: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
    ) {
        val artHeight = (maxHeight * .27f).coerceIn(120.dp, 230.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("◉  roadsignal", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Lime)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                RouteArt(Modifier.fillMaxWidth().height(artHeight))
                Text(
                    "The drive is only\npart of the story.",
                    fontSize = 39.sp,
                    lineHeight = 43.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    "Explore the signals, decisions and tradeoffs behind an Android telematics SDK.",
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    color = Color.White.copy(alpha = .75f),
                )
                Text(
                    "Synthetic trips first. Device capture only by choice.\nAn independent SDK engineering lab.",
                    color = Lime,
                    lineHeight = 23.sp,
                )
            }
            Button(
                onClick = done,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
            ) {
                Text("Explore the lab", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("◉ roadsignal", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Tag("FIELD LAB · 0.1", Lime)
    }
}

@Composable
private fun Heading(title: String, eyebrow: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            eyebrow,
            color = Teal,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(title, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Section(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Panel(color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Line)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(30.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            letterSpacing = .4.sp,
        )
    }
}

@Composable
private fun Note(text: String) {
    Surface(color = Color(0xFFE7ECE2), shape = RoundedCornerShape(16.dp)) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(14.dp),
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier,
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(value, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 8.sp, color = Muted, letterSpacing = .6.sp)
        }
    }
}

@Composable
private fun Hero(s: SdkSnapshot, replay: () -> Unit) {
    Panel(Ink) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Tag("SYNTHETIC SCENARIO", Lime)
            Text("01 / 04", color = Lime, fontSize = 12.sp)
        }
        RouteArt(Modifier.fillMaxWidth().height(125.dp))
        Text(
            "The city commute",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            "Detect a trip, inspect its signals, and follow it into the durable outbox.",
            color = Color.White.copy(alpha = .7f),
            lineHeight = 21.sp,
        )
        Button(
            onClick = replay,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
        ) {
            Text(if (s.consent) "Replay commute" else "Review local consent")
        }
    }
}

@Composable
private fun RouteArt(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Illustrated route, not a geographic map" }) {
        val w = size.width
        val h = size.height
        for (i in 1..6) drawLine(
            Color.White.copy(alpha = .08f),
            Offset(w * i / 7, 0f),
            Offset(w * i / 7, h),
            1.dp.toPx(),
        )
        for (i in 1..4) drawLine(
            Color.White.copy(alpha = .08f),
            Offset(0f, h * i / 5),
            Offset(w, h * i / 5),
            1.dp.toPx(),
        )
        val p =
            Path().apply {
                moveTo(w * .08f, h * .78f)
                cubicTo(w * .28f, h * .78f, w * .25f, h * .23f, w * .44f, h * .23f)
                lineTo(w * .63f, h * .23f)
                cubicTo(w * .82f, h * .23f, w * .67f, h * .68f, w * .91f, h * .5f)
            }
        drawPath(p, Lime, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Orange, 9.dp.toPx(), Offset(w * .08f, h * .78f))
        drawCircle(Lime, 9.dp.toPx(), Offset(w * .91f, h * .5f))
        drawCircle(Ink, 4.dp.toPx(), Offset(w * .91f, h * .5f))
    }
}

@Composable
private fun ConsentCard(review: () -> Unit) {
    Panel {
        Text("Nothing starts without consent.", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "You can explore every screen first. Enable the local SDK when you’re ready to run a fixture.",
            color = Muted,
        )
        OutlinedButton(onClick = review) { Text("Review consent") }
    }
}

@Composable
private fun ScenarioCard(s: ReplayScenario, enabled: Boolean, run: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = run),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                if (s == ReplayScenario.SENSOR_GAP)
                    Icons.Outlined.SignalCellularConnectedNoInternet0Bar
                else if (s == ReplayScenario.IMPACT) Icons.Outlined.WarningAmber
                else Icons.Outlined.PlayArrow,
                null,
                tint = Teal,
            )
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.Bold)
                Text(s.description, fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
private fun EngineCard(s: SdkSnapshot) {
    Panel {
        Tag("ENGINE · ${s.source.name}", Lime)
        Text(s.recording.name, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(
            "${s.acceptedSamples} accepted · ${s.rejectedSamples} rejected · ${s.droppedSamples} dropped",
            fontSize = 12.sp,
            color = Muted,
        )
        Text(s.notice, fontSize = 13.sp, lineHeight = 20.sp)
        if (s.recording != RecordingState.STOPPED)
            Text(
                "Capture is active. Use Controls → Stop capture to end it.",
                color = Teal,
                fontSize = 12.sp,
            )
    }
}

@Composable
private fun Empty(title: String, description: String) {
    Panel {
        Icon(Icons.Outlined.Route, null, tint = Teal, modifier = Modifier.size(36.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Muted, lineHeight = 22.sp)
    }
}

@Composable
private fun TripCard(trip: TripSummary, open: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(23.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().clickable(onClick = open),
    ) {
        Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Tag(trip.source.name, if (trip.source == SourceMode.REPLAY) Lime else Orange)
                Text("${trip.coachingScore}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                if (trip.source == SourceMode.REPLAY) "Synthetic city route" else "Device session",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${(trip.distanceMeters/1000).f(2)} km · ${(trip.endElapsedMs-trip.startElapsedMs)/1000} s · ${trip.events.size} events",
                color = Muted,
            )
            Text("${trip.role.name} · Open trip details →", fontSize = 12.sp, color = Teal)
        }
    }
}

@Composable
private fun TripHeader(t: TripSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tag("${t.source.name} · PERSISTED LOCALLY", Lime)
        Text("A drive, decoded.", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("COACHING INDEX", t.coachingScore.toString(), Modifier.weight(1f))
            Metric("DISTANCE KM", (t.distanceMeters / 1000).f(2), Modifier.weight(1f))
            Metric("MAX KM/H", (t.maxSpeedMps * 3.6).f(0), Modifier.weight(1f))
        }
    }
}

@Composable
private fun RouteSketch(points: List<RoutePoint>, synthetic: Boolean) {
    Panel(Ink) {
        Tag(if (synthetic) "SYNTHETIC ROUTE SKETCH" else "DEVICE ROUTE SKETCH", Lime)
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).semantics {
                contentDescription = "Trip route polyline without map tiles"
            },
        ) {
            if (points.size > 1) {
                val minX = points.minOf { it.longitude }
                val maxX = points.maxOf { it.longitude }
                val minY = points.minOf { it.latitude }
                val maxY = points.maxOf { it.latitude }
                val path = Path()
                points.forEachIndexed { index, p ->
                    val x =
                        16 + (p.longitude - minX) / maxOf(maxX - minX, .00001) * (size.width - 32)
                    val y =
                        size.height -
                            16 -
                            (p.latitude - minY) / maxOf(maxY - minY, .00001) * (size.height - 32)
                    if (index == 0) path.moveTo(x.toFloat(), y.toFloat())
                    else path.lineTo(x.toFloat(), y.toFloat())
                }
                drawPath(path, Lime, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(
            "Route geometry only · no online map service",
            color = Color.White.copy(alpha = .6f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DeviceControls(s: SdkSnapshot, vm: LabViewModel) {
    val context = LocalContext.current
    val liveActive = s.source == SourceMode.DEVICE && s.recording != RecordingState.STOPPED
    var refreshKey by remember { mutableIntStateOf(0) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshKey++
        }
    val fine =
        remember(refreshKey) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    val coarse =
        remember(refreshKey) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }
    Panel {
        Text("Device session", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Starts only from this visible screen. A foreground notification provides a Stop action. No background-location permission is requested.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Text(
            if (fine) "Location: precise access granted"
            else if (coarse) "Location: approximate only; poor fixes can be rejected"
            else "Location: not granted",
            color = Teal,
            fontSize = 12.sp,
        )
        OutlinedButton(
            onClick = {
                launcher.launch(
                    buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                        .toTypedArray(),
                )
            },
            enabled = s.consent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Request location access")
        }
        Button(
            onClick = { vm.live(context as Activity) },
            enabled = s.consent && !liveActive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (liveActive) "Device session active" else "Start device session")
        }
        OutlinedButton(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) {
            Text("Stop capture")
        }
    }
}

@Composable
private fun DeleteControl(s: SdkSnapshot, vm: LabViewModel) {
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        modifier = Modifier.fillMaxWidth(),
        enabled = s.consent || s.trips.isNotEmpty(),
    ) {
        Text("Revoke consent & delete data")
    }
    if (confirm)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Stop capture and delete data?") },
            text = {
                Text(
                    "This stops recording, cancels scheduled sync and deletes local trips, outbox entries and mock receipts. You can enable consent again later.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        vm.revoke()
                    },
                ) {
                    Text("Delete local data")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep data") } },
        )
}

private fun LazyListScope.labContent(
    s: SdkSnapshot,
    vm: LabViewModel,
    section: String,
    select: (String) -> Unit,
) {
    item { Heading("Behind the drive.", "ENGINEERING WORKBENCH") }
    item {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Power", "Outbox", "Pipeline", "Diagnostics").forEach { title ->
                FilterChip(
                    selected = section == title,
                    onClick = { select(title) },
                    label = { Text(title) },
                )
            }
        }
    }
    when (section) {
        "Power" -> {
            item {
                Panel(Ink) {
                    Tag("COUNTERFACTUAL MODEL", Lime)
                    Text(
                        "Keep the signal.\nQuestion the lock.",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "For a modeled 180-second recording:",
                        color = Color.White.copy(alpha = .7f),
                    )
                    Text(
                        "Legacy explicit lock: 180 s\nRoadSignal explicit lock: 0 s",
                        color = Lime,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                    )
                    Text(
                        "This is a model of SDK ownership, not measured battery savings or an Android vitals result. Foreground services do not guarantee CPU wakefulness or process survival.",
                        color = Color.White.copy(alpha = .7f),
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                Section(
                    "Sampling policy",
                    "Current request: ${s.profile.sensorHz} Hz · ${s.profile.batchLatencyUs/1_000_000} s maximum sensor batching. Hardware support and actual delivery vary.",
                )
            }
            items(PowerProfile.entries) { profile ->
                OutlinedButton(
                    onClick = { vm.policy(profile) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Apply ${profile.name.lowercase()} policy")
                }
            }
            item {
                Text(
                    "Local policy revision: ${s.policyRevision}. Fixtures expire after one hour. Applying a profile reconfigures active device listeners.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            item {
                Button(
                    onClick = { vm.policy(s.profile, false) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disable collection by policy")
                }
            }
            item {
                Note(
                    "No remote config server is deployed. The version/TTL gate and immediate collection-disable path are real; policy transport, staged targeting and authenticity are documented design work.",
                )
            }
        }
        "Outbox" -> {
            item {
                Section(
                    "Durable by design",
                    "Encrypted trip payloads and a unique trip-ID outbox survive Activity and process recreation.",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("PENDING", s.pendingUploads.toString(), Modifier.weight(1f))
                    Metric("DEAD LETTERS", s.deadLetters.toString(), Modifier.weight(1f))
                    Metric("LOCAL ACKS", s.localReceipts.toString(), Modifier.weight(1f))
                }
            }
            item { Tag("LOCAL MOCK TRANSPORT", Orange) }
            item {
                Text("Choose the server response", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            item {
                Text(
                    "No HTTP request or CMT upload occurs. WorkManager still runs a durable, constrained job.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            items(UploadSimulation.entries) { mode ->
                FilterChip(
                    selected = s.uploadSimulation == mode,
                    onClick = { vm.uploadMode(mode) },
                    label = { Text(mode.name.replace('_', ' ')) },
                )
            }
            item {
                Button(
                    onClick = vm::sync,
                    enabled = s.consent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Schedule local sync")
                }
            }
            item {
                OutlinedButton(
                    onClick = vm::retry,
                    enabled = s.deadLetters > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry dead letters")
                }
            }
            item {
                Note(
                    "Retryable outcomes use exponential backoff. Five unsuccessful attempts become dead letters. Permanent failures do not retry automatically. A local receipt key deduplicates acknowledgments; it is not a distributed exactly-once guarantee.",
                )
            }
        }
        "Pipeline" -> {
            item {
                Panel(Ink) {
                    Tag("SDK ${RoadSignal.VERSION}", Lime)
                    Text(
                        "Build → publish\n→ consume → verify",
                        color = Color.White,
                        fontSize = 27.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "JVM core + Android AARs + source archives + Maven metadata",
                        color = Color.White.copy(alpha = .75f),
                    )
                }
            }
            item {
                Section(
                    "Contained distribution",
                    "A project-local Maven repository and an independent consumer module exercise the artifacts clients receive.",
                )
            }
            items(
                listOf(
                    "1 · Validate core behavior and public API baseline",
                    "2 · Build and lint SDK + host; shrink the release consumer",
                    "3 · Publish immutable versioned Maven coordinates",
                    "4 · Rebuild consumer from POM/AAR dependencies",
                    "5 · Attach repository ZIP, checksums and provenance",
                    "6 · Publish, activate policy, verify partner adoption",
                ),
            ) {
                Text(it, fontSize = 14.sp, lineHeight = 21.sp)
            }
            item {
                Note(
                    "GitHub Actions and scripts contain the executable pipeline. No Maven Central or GitHub Packages credentials are required. Consumer source and CI runs are linked from the repository README.",
                )
            }
        }
        "Diagnostics" -> {
            item { EngineCard(s) }
            item {
                Text(
                    "Latest gyroscope peak: ${s.latestRotationRadPerSec.f(2)} rad/s",
                    color = Muted,
                )
            }
            items(vm.sdk.sensorCapabilities()) { capability ->
                Text(
                    "${capability.type}: ${if(capability.available) "available" else "unavailable"} · FIFO ${capability.fifoMaxEvents} · wake-up ${capability.wakeUp}",
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
            item { Text("Unreadable encrypted records: ${s.unreadableRecords}", color = Muted) }
            item {
                Section(
                    "Bounded local diagnostics",
                    "Fixed operation messages only. No raw coordinates, account IDs or tokens enter this log.",
                )
            }
            items(s.diagnostics.asReversed()) {
                Text("• $it", fontSize = 12.sp, color = Muted, lineHeight = 19.sp)
            }
            item {
                Note(
                    "Sample queue capacity: 64. Dropped events are counted. Route retention: 500 points/trip; event retention: 200/trip; journal: 50 trips. No production telemetry endpoint is configured.",
                )
            }
        }
    }
}
