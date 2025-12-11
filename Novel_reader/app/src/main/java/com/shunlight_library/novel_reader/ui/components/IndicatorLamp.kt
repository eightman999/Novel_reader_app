/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Indicator lamp UI component for displaying processing status.
 */
package com.shunlight_library.novel_reader.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.novel_reader.data.ProcessingState
import com.shunlight_library.novel_reader.data.ProcessingStatusType

/**
 * インジケーターランプの表示スタイル
 */
enum class IndicatorLampStyle {
    SOLID,    // 点灯（常時表示）
    BLINKING  // 点滅
}

/**
 * インジケーターランプのグループ表示
 *
 * @param states 処理状態のリスト
 * @param style 表示スタイル（点灯/点滅）
 * @param enabled 表示の有効/無効
 * @param modifier Modifier
 */
@Composable
fun IndicatorLampGroup(
    states: List<ProcessingState>,
    style: IndicatorLampStyle = IndicatorLampStyle.SOLID,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!enabled || states.isEmpty()) {
        return
    }

    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedState by remember { mutableStateOf<ProcessingState?>(null) }

    // 最大表示数を制限（例：5個まで）
    val displayStates = states.take(5)

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        displayStates.forEach { state ->
            IndicatorLamp(
                state = state,
                style = style,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        selectedState = state
                        showDetailDialog = true
                    }
            )
        }

        // 残りの数を表示
        if (states.size > 5) {
            Text(
                text = "+${states.size - 5}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }

    // 詳細ダイアログ
    if (showDetailDialog && selectedState != null) {
        ProcessingDetailDialog(
            state = selectedState!!,
            onDismiss = { showDetailDialog = false }
        )
    }
}

/**
 * 単一のインジケーターランプ
 *
 * @param state 処理状態
 * @param style 表示スタイル
 * @param modifier Modifier
 */
@Composable
fun IndicatorLamp(
    state: ProcessingState,
    style: IndicatorLampStyle = IndicatorLampStyle.SOLID,
    modifier: Modifier = Modifier
) {
    val color = state.statusType.color

    // 点滅アニメーション
    val alpha = when (style) {
        IndicatorLampStyle.SOLID -> 1f
        IndicatorLampStyle.BLINKING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "blinking")
            infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            ).value
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * 処理詳細ダイアログ
 *
 * @param state 処理状態
 * @param onDismiss ダイアログを閉じる処理
 */
@Composable
fun ProcessingDetailDialog(
    state: ProcessingState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("処理状況")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // タイトル
                Row {
                    Text(
                        text = "小説: ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = state.novelTitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // 状態
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "状態: ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(state.statusType.color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.statusType.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // 進捗
                if (state.totalEpisodes > 0) {
                    Column {
                        Text(
                            text = "進捗: ${state.currentEpisode} / ${state.totalEpisodes}話",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = state.calculateProgress(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                    }
                }

                // 経過時間
                Text(
                    text = "経過時間: ${state.getElapsedSeconds()}秒",
                    style = MaterialTheme.typography.bodySmall
                )

                // エラーメッセージ
                if (state.errorMessage != null) {
                    Text(
                        text = "エラー: ${state.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
