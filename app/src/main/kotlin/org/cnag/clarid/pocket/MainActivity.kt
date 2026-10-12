package org.cnag.clarid.pocket

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cnag.clarid.core.Biosample
import org.cnag.clarid.core.DecodeSettings
import org.cnag.clarid.core.IdentifierFormat
import org.cnag.clarid.core.IdentifierEntity
import org.cnag.clarid.core.IdentifierRecord
import org.cnag.clarid.core.Subject
import org.cnag.clarid.core.Term

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PocketScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PocketScreen(model: PocketViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) model.startScan()
    }
    val startScan: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            permissionDenied = false
            model.startScan()
        } else permission.launch(Manifest.permission.CAMERA)
    }
    BackHandler(enabled = state.scanning) { model.stopScan() }

    if (state.scanning) {
        var scanFeedback by remember { mutableStateOf(ScanFeedback()) }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Scan a ClarID QR", style = MaterialTheme.typography.headlineSmall)
            Text(entityLabel(state.settings.entity) + " · " +
                state.settings.format.name.lowercase().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                QrCamera(Modifier.fillMaxSize(), onScanned = model::scanned, onError = model::scanError,
                    onFeedback = { scanFeedback = it })
                ScanGuide(Modifier.fillMaxSize())
            }
            Text(if (scanFeedback.ambiguous) "Aim the centre crosshair at the QR you want."
                else "Point at a QR code to scan.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Tap to focus · Pinch to zoom", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = model::stopScan, modifier = Modifier.fillMaxWidth()) { Text("Stop scanning") }
        }
        return
    }

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ConfigurationCard(state, model::configure, model::checkForUpdates)
                TextButton(onClick = { settingsOpen = false }, modifier = Modifier.align(Alignment.End)) {
                    Text("Done")
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = HeaderNavy, contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.ic_clarid), contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                        Text("ClarID-Pocket", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { settingsOpen = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = SubjectBlue, contentColor = Color.White),
                            modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Decode settings",
                                modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Identifier decoding", style = MaterialTheme.typography.headlineLarge)
                }
            }
        }
        if (state.loading || state.processing) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(if (state.loading) "Loading codebook and disease labels…" else "Decoding…")
                }
            }
        }
        item {
            Button(onClick = startScan, enabled = state.ready && !state.processing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (state.result != null || state.error != null) "Scan again" else "Scan QR")
            }
        }
        if (permissionDenied) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Camera permission is needed to scan a QR code.")
                        Text("Tap Scan QR to retry, or enable Camera in the app’s permissions.")
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                ("package:" + context.packageName).toUri()))
                        }) { Text("Open app settings") }
                    }
                }
            }
        }
        state.error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Unable to decode", style = MaterialTheme.typography.titleMedium)
                        Text(message)
                    }
                }
            }
        }
        state.result?.let { result ->
            item { DecodedResult(result,
                state.settings.copy(format = state.detectedFormat ?: state.settings.format, entity = result.entity),
                state.identifier.orEmpty(), state.settings.entity == IdentifierEntity.AUTO) }
        }
    }
}

@Composable
private fun ConfigurationCard(state: PocketState, onChange: (DecodeSettings) -> Unit, onCheckUpdates: () -> Unit) {
    val context = LocalContext.current
    var browserError by remember { mutableStateOf(false) }
    val settings = state.settings
    val enabled = state.ready && !state.processing
    var advanced by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Decode settings", style = MaterialTheme.typography.titleMedium)
                StatusLabel("ClarID " + state.specificationVersion.ifEmpty { "…" })
            }
            HorizontalDivider()
            Text("Identifier entity", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((entity, label) in listOf(IdentifierEntity.AUTO to "Auto",
                    IdentifierEntity.BIOSAMPLE to "Biosample", IdentifierEntity.SUBJECT to "Subject")) {
                    FilterChip(selected = settings.entity == entity, enabled = enabled,
                        onClick = { onChange(settings.copy(entity = entity)) }, label = { Text(label) })
                }
            }
            Text("Identifier format", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((format, label) in listOf(IdentifierFormat.AUTO to "Auto",
                    IdentifierFormat.HUMAN to "Human", IdentifierFormat.STUB to "Stub")) {
                    FilterChip(selected = settings.format == format, enabled = enabled,
                        onClick = { onChange(settings.copy(format = format)) }, label = { Text(label) })
                }
            }
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Hide advanced settings" else "Advanced settings")
            }
            if (advanced) {
                WidthSetting("Human subject width · Decimal digits", settings.humanSubjectWidth, 18, enabled) {
                    onChange(settings.copy(humanSubjectWidth = it))
                }
                WidthSetting("Stub subject width · Base62 characters", settings.stubSubjectWidth, 10, enabled) {
                    onChange(settings.copy(stubSubjectWidth = it))
                }
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(12.dp)) {
                    Text("Use the widths used to create the identifier. A different width may produce " +
                        "a plausible but incorrect result. Auto does not guess widths.",
                        modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text("About", style = MaterialTheme.typography.titleSmall)
            }
            Text("App version: " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { browserError = false; onCheckUpdates() },
                enabled = !state.checkingUpdates, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.checkingUpdates) "Checking…" else "Check for updates")
            }
            state.appUpdate?.let { update ->
                Text(update.message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                update.downloadPage?.let { page ->
                    Button(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, page.toUri()))
                            browserError = false
                        } catch (_: android.content.ActivityNotFoundException) {
                            browserError = true
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open download page") }
                }
            }
            if (browserError) Text("No browser is available to open GitHub.")
            Text("Checks app releases on GitHub. Scanning stays offline.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Bundled codebook · " + state.revision.ifEmpty { "Loading…" },
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WidthSetting(label: String, width: Int, maximum: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = { onChange(width - 1) }, enabled = enabled && width > 1,
            modifier = Modifier.semantics { contentDescription = "Decrease $label" }) { Text("−") }
        Text(width.toString(), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { onChange(width + 1) }, enabled = enabled && width < maximum,
            modifier = Modifier.semantics { contentDescription = "Increase $label" }) { Text("+") }
    }
}

@Composable
private fun DecodedResult(result: IdentifierRecord, settings: DecodeSettings, identifier: String, autoEntity: Boolean) {
    val colors = MaterialTheme.colorScheme
    val subject = result is Subject
    val accent = if (subject) colors.secondary else colors.primary
    val container = if (subject) SubjectBlue else BiosampleTeal
    val onContainer = Color.White
    CompositionLocalProvider(LocalFieldAccent provides accent) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = container,
                    contentColor = onContainer,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = successContainer(), contentColor = successContent(),
                            shape = RoundedCornerShape(24.dp)) {
                            Icon(painterResource(R.drawable.ic_success), contentDescription = "Decoded successfully",
                                modifier = Modifier.padding(8.dp).size(32.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text((if (autoEntity) "Detected: " else "Decoded: ") + entityLabel(result.entity),
                                style = MaterialTheme.typography.titleLarge)
                            Text(configurationCaption(settings), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Surface(color = colors.surfaceVariant, contentColor = colors.onSurface,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scanned identifier", style = MaterialTheme.typography.labelMedium)
                        Text(identifier, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (result) {
                            is Biosample -> {
                                TermField("Project", result.project)
                                TermField("Species", result.species)
                                ValueField("Subject ID", result.subjectId.toString())
                                TermField("Tissue", result.tissue)
                                TermField("Sample type", result.sampleType)
                                TermField("Assay", result.assay)
                                HorizontalDivider()
                                TermField("Timepoint", result.timepoint)
                                ValueField("Duration", result.duration)
                                result.batch?.let { ValueField("Batch", it.toString()) }
                                result.replicate?.let { ValueField("Replicate", it.toString()) }
                            }
                            is Subject -> {
                                ValueField("Study", result.studyLabel?.let { it + " · " + result.study } ?: result.study)
                                ValueField("Subject ID", result.subjectId.toString())
                                TermField("Type", result.type)
                                TermField("Sex", result.sex)
                                TermField("Age group", result.ageGroup)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldIcon(R.drawable.ic_health)
                    Text("Diseases · ICD-10", style = MaterialTheme.typography.titleLarge)
                }
                result.conditions.forEach { disease ->
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(disease.code, style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace)
                            Text(disease.label ?: "Label unavailable", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ScanGuide(modifier: Modifier) {
    Canvas(modifier) {
        val side = size.minDimension * ScanTarget.GUIDE_FRACTION
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val segment = side * 0.12f
        val stroke = 3.dp.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        val arm = 8.dp.toPx()
        drawLine(Color.White, centre - Offset(arm, 0f), centre + Offset(arm, 0f), 2.dp.toPx())
        drawLine(Color.White, centre - Offset(0f, arm), centre + Offset(0f, arm), 2.dp.toPx())
        for ((x, directionX) in listOf(left to 1f, (left + side) to -1f)) {
            for ((y, directionY) in listOf(top to 1f, (top + side) to -1f)) {
                drawLine(Color.White, Offset(x, y), Offset(x + segment * directionX, y), stroke)
                drawLine(Color.White, Offset(x, y), Offset(x, y + segment * directionY), stroke)
            }
        }
    }
}

@Composable
private fun TermField(name: String, term: Term) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldIcon(fieldIcon(name))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(term.label, style = MaterialTheme.typography.bodyLarge)
        Text(term.code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ValueField(name: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldIcon(fieldIcon(name))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private val LocalFieldAccent = staticCompositionLocalOf { Color.Unspecified }

@Composable
private fun FieldIcon(resource: Int) {
    Icon(painterResource(resource), contentDescription = null,
        tint = LocalFieldAccent.current, modifier = Modifier.size(20.dp))
}

private fun fieldIcon(name: String): Int = when (name) {
    "Project", "Study" -> R.drawable.ic_study
    "Subject ID", "Type", "Sex", "Age group" -> R.drawable.ic_person
    "Species" -> R.drawable.ic_species
    "Tissue", "Sample type" -> R.drawable.ic_sample
    "Assay" -> R.drawable.ic_assay
    "Timepoint" -> R.drawable.ic_calendar
    "Duration" -> R.drawable.ic_clock
    "Batch", "Replicate" -> R.drawable.ic_layers
    else -> R.drawable.ic_info
}

private fun configurationCaption(settings: DecodeSettings): String = when (settings.format) {
    IdentifierFormat.AUTO -> entityLabel(settings.entity) + " · Auto format"
    IdentifierFormat.STUB -> "Stub · Subject width " + settings.stubSubjectWidth
    IdentifierFormat.HUMAN -> "Human · Subject width " + settings.humanSubjectWidth
}

private fun entityLabel(entity: IdentifierEntity) = when (entity) {
    IdentifierEntity.AUTO -> "Subject or biosample"
    IdentifierEntity.BIOSAMPLE -> "Biosample"
    IdentifierEntity.SUBJECT -> "Subject"
}
