package com.shunlight_library.novel_reader.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.export.DatabaseExportManager
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import com.shunlight_library.novel_reader.ui.theme.Novel_readerTheme
import kotlinx.coroutines.launch

class DatabaseExportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Novel_readerTheme {
                DatabaseExportScreen { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository: NovelRepository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()

    val novels by repository.allNovels.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(setOf<String>()) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ) { uri ->
        exportUri = uri
    }

    fun toggle(ncode: String) {
        selected = if (selected.contains(ncode)) selected - ncode else selected + ncode
    }

    fun startExport() {
        val uri = exportUri ?: return
        val ncodes = selected.toList()
        isExporting = true
        resultMessage = null
        scope.launch {
            val manager = DatabaseExportManager(context)
            val success = manager.exportSelectedNovels(uri, ncodes)
            resultMessage = if (success) "書き出し完了" else "書き出し失敗"
            isExporting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DB書き出し") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            Button(
                onClick = { createDocumentLauncher.launch("novels_export.db") },
                enabled = !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(exportUri?.let { "保存先: $it" } ?: "保存先を選択")
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(novels) { novel ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(novel.ncode) }
                            .padding(8.dp)
                    ) {
                        Checkbox(
                            checked = selected.contains(novel.ncode),
                            onCheckedChange = { toggle(novel.ncode) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(novel.title)
                    }
                }
            }
            resultMessage?.let {
                Text(it, modifier = Modifier.padding(16.dp))
            }
            Button(
                onClick = { startExport() },
                enabled = exportUri != null && selected.isNotEmpty() && !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(if (isExporting) "書き出し中..." else "書き出し")
            }
        }
    }
}
