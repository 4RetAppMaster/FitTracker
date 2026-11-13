package com.example.ui.backup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupDialog(
    exportDataString: String,
    exportCsvString: String = "",
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onImportData: (data: String, replace: Boolean) -> Unit,
    onRestoreUploadedBackup: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var exportFormat by remember { mutableIntStateOf(0) } // 0 = CSV, 1 = JSON
    var importText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<String?>(null) }
    var replaceExisting by remember { mutableStateOf(false) }
    var showConfirmImportDialog by remember { mutableStateOf(false) }

    // Save File Launchers
    var pendingExportContent by remember { mutableStateOf("") }
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingExportContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "CSV file saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving CSV file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingExportContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "JSON backup saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving JSON backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Open File Launcher (File Picker)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = getFileName(context, uri) ?: "backup_file"
                var sizeText: String? = null
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1) {
                            val bytes = cursor.getLong(sizeIdx)
                            sizeText = if (bytes > 1024) "${bytes / 1024} KB" else "$bytes B"
                        }
                    }
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader(Charsets.UTF_8).readText()
                    importText = content
                    selectedFileName = fileName
                    selectedFileSize = sizeText
                    Toast.makeText(context, "Loaded file: $fileName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Detect format of import text
    val detectedFormat = remember(importText) {
        val trimmed = importText.trim()
        when {
            trimmed.isEmpty() -> null
            trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.contains("\"exportVersion\"") -> "JSON Backup"
            trimmed.contains("--- WORKOUT HISTORY ---") || trimmed.contains("Session ID,Date") || trimmed.lines().any { it.contains(",") } -> "CSV Spreadsheet"
            else -> "Data File"
        }
    }

    val todayDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) {
            if (showConfirmImportDialog) {
                showConfirmImportDialog = false
            } else {
                onDismiss()
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            tonalElevation = 6.dp
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Export & Import Data",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp)
                ) {
                    // Tab row
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Export & Download", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Import & Restore", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) }
                        )
                    }

                    if (selectedTab == 0) {
                        // EXPORT TAB - Fully scrollable with weight(1f)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Format selector (CSV vs JSON)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterChip(
                                    selected = exportFormat == 0,
                                    onClick = { exportFormat = 0 },
                                    label = { Text("CSV Spreadsheet", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = exportFormat == 1,
                                    onClick = { exportFormat = 1 },
                                    label = { Text("JSON Backup", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (exportFormat == 0) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = Color(0xFF0D9488)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Complete Workout & Lockers CSV",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Exports all workout sessions & logs, PR records, workout plans with target exercises, and active gym lockers in organized CSV sections.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Export Actions Row 1: Save to File + Share
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            pendingExportContent = exportCsvString
                                            saveCsvLauncher.launch("FitTracker_History_$todayDateStr.csv")
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("save_export_csv_file_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                                    ) {
                                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save File")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, exportCsvString)
                                                    putExtra(Intent.EXTRA_SUBJECT, "FitTracker_History_$todayDateStr.csv")
                                                    type = "text/csv"
                                                }
                                                val shareIntent = Intent.createChooser(sendIntent, "Export Workout CSV")
                                                context.startActivity(shareIntent)
                                            } catch (e: Exception) {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, exportCsvString)
                                                    type = "text/plain"
                                                }
                                                val shareIntent = Intent.createChooser(sendIntent, "Export Workout CSV")
                                                context.startActivity(shareIntent)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("share_export_csv_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF0D9488))
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share / Send", color = Color(0xFF0D9488))
                                    }
                                }

                                // Copy to Clipboard
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Workout CSV", exportCsvString)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied CSV to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("copy_export_csv_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy CSV to Clipboard")
                                }

                                Text(
                                    text = "CSV PREVIEW",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = exportCsvString.ifEmpty { "Generating CSV..." },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Complete System JSON Backup",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Exports all workout plans, plan exercises, custom exercises, workout history & set logs, personal records (PRs), and active gym lockers in a single transferable JSON file.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Export Actions Row: Save to File + Share
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            pendingExportContent = exportDataString
                                            saveJsonLauncher.launch("FitTracker_Backup_$todayDateStr.json")
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("save_export_json_file_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save File")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, exportDataString)
                                                putExtra(Intent.EXTRA_SUBJECT, "FitTracker_Backup_$todayDateStr.json")
                                                type = "application/json"
                                            }
                                            val shareIntent = Intent.createChooser(sendIntent, "Relocate / Share Workout Backup")
                                            context.startActivity(shareIntent)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("share_export_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share / Send")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Workout Backup", exportDataString)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied backup JSON to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("copy_export_json_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy JSON to Clipboard")
                                }

                                Text(
                                    text = "EXPORTED JSON PREVIEW",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = exportDataString,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Bottom spacer in Export tab for easy scrolling on small phones
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    } else {
                        // IMPORT TAB - Fully scrollable with weight(1f)
                        val importScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(importScrollState)
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Select an exported backup file (.json or .csv) from your device, or paste the file contents below to import your data:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 1. SELECT FILE BUTTON (Primary action)
                            Button(
                                onClick = {
                                    openFileLauncher.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "text/plain",
                                            "*/*"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("pick_import_file_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Backup File (.json / .csv)", fontWeight = FontWeight.Bold)
                            }

                            if (onRestoreUploadedBackup != null) {
                                FilledTonalButton(
                                    onClick = onRestoreUploadedBackup,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFFFFF7ED),
                                        contentColor = Color(0xFFFF6B00)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("restore_uploaded_backup_button")
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Restore Uploaded CSV Backup", fontWeight = FontWeight.Bold)
                                    }
                            }

                            // Show Selected File Badge
                            if (selectedFileName != null) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = selectedFileName ?: "Selected file",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (selectedFileSize != null) {
                                                    Text(
                                                        text = selectedFileSize!!,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                                if (detectedFormat != null) {
                                                    Text(
                                                        text = "• $detectedFormat",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedFileName = null
                                                selectedFileSize = null
                                                importText = ""
                                            }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear file", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            }

                            // 2. PASTE FROM CLIPBOARD
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)
                                    val text = item?.text?.toString()
                                    if (!text.isNullOrBlank()) {
                                        importText = text
                                        selectedFileName = "Clipboard data"
                                        selectedFileSize = "${text.length} chars"
                                        Toast.makeText(context, "Pasted from clipboard!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Paste from Clipboard")
                            }

                            // 3. TEXT PREVIEW / INPUT
                            OutlinedTextField(
                                value = importText,
                                onValueChange = {
                                    importText = it
                                    if (selectedFileName == null && it.isNotBlank()) {
                                        selectedFileName = "Custom pasted input"
                                    }
                                },
                                maxLines = 4,
                                minLines = 2,
                                placeholder = { Text("Or paste JSON / CSV content directly here...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 70.dp, max = 110.dp)
                                    .testTag("import_json_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            if (detectedFormat != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Detected: $detectedFormat",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            // 4. REPLACE VS MERGE CHECKBOX
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (replaceExisting) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (replaceExisting) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { replaceExisting = !replaceExisting }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Checkbox(
                                        checked = replaceExisting,
                                        onCheckedChange = { replaceExisting = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.error,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Replace existing data completely",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (replaceExisting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (replaceExisting)
                                                "Warning: Current workout plans, exercises, logs, and lockers will be wiped and replaced."
                                            else
                                                "If unchecked, records and lockers will be safely merged with current library.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 5. RESTORE BUTTON - Prominent and clearly visible
                            Button(
                                onClick = { showConfirmImportDialog = true },
                                enabled = importText.isNotBlank() && !isImporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("submit_import_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (replaceExisting) Color(0xFFE11D48) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restoring Data...")
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (replaceExisting) "Overwrite & Restore Data" else "Import & Merge Data",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            // Generous bottom spacer so on any small phone screen, the user can easily scroll well past the button
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    if (showConfirmImportDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmImportDialog = false },
            title = { Text("Confirm Data Import", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (replaceExisting)
                        "Warning: This will overwrite and replace all current workout plans, logs, custom exercises, and active lockers with the backup file data. Do you want to proceed?"
                    else
                        "This will import and merge the backup file data (workout plans, exercises, logs, and lockers) into your existing library without deleting your current records. Do you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmImportDialog = false
                        onImportData(importText, replaceExisting)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (replaceExisting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Proceed Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = cursor.getString(index)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}
