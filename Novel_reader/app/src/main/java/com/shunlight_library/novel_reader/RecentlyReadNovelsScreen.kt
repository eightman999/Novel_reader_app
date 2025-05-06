package com.shunlight_library.novel_reader

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LastReadNovelWithInfo(
    val lastRead: LastReadNovelEntity,
    val novel: NovelDescEntity?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyReadNovelsScreen(
    onBack: () -> Unit,
    onNovelClick: (String, String) -> Unit // ncode, episodeNo
) {
    val repository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()

    // 状態変数
    var recentlyReadNovels by remember { mutableStateOf<List<LastReadNovelWithInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // データの取得
    LaunchedEffect(Unit) {
        // 最後に読んだ履歴を日付順に取得
        val lastReadNovels = repository.allLastReadNovels.first()

        // 各履歴に対応する小説情報を取得
        val novelWithInfoList = lastReadNovels.map { lastRead ->
            val novel = repository.getNovelByNcode(lastRead.ncode)
            LastReadNovelWithInfo(lastRead, novel)
        }

        recentlyReadNovels = novelWithInfoList
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最近読んだ小説") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (recentlyReadNovels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("最近読んだ小説はありません")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(recentlyReadNovels) { item ->
                    RecentlyReadNovelItem(
                        lastReadNovel = item.lastRead,
                        novel = item.novel,
                        onClick = { onNovelClick(item.lastRead.ncode, item.lastRead.episode_no.toString()) }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun RecentlyReadNovelItem(
    lastReadNovel: LastReadNovelEntity,
    novel: NovelDescEntity?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (novel != null) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "作者: ${novel.author}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "第${lastReadNovel.episode_no}話",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "最終閲覧: ${lastReadNovel.date}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (novel.total_ep > lastReadNovel.episode_no) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "未読: ${novel.total_ep - lastReadNovel.episode_no}話",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                Text(
                    text = "Nコード: ${lastReadNovel.ncode}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "エピソード: ${lastReadNovel.episode_no}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "最終閲覧: ${lastReadNovel.date}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "※小説情報が見つかりません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}