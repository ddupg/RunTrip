package com.ddupg.runtrip.feature.pace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun FinishTimePickerSheet(
    initialSeconds: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = initialSeconds ?: 0L
    var hours by remember(initialSeconds) { mutableIntStateOf((initial / 3_600).toInt()) }
    var minutes by remember(initialSeconds) { mutableIntStateOf((initial % 3_600 / 60).toInt()) }
    var seconds by remember(initialSeconds) { mutableIntStateOf((initial % 60).toInt()) }
    var showError by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择完赛时间",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消选择时间")
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberWheel(
                    label = "小时",
                    values = 0..99,
                    selected = hours,
                    onSelected = {
                        hours = it
                        showError = false
                    },
                    modifier = Modifier.weight(1f),
                )
                NumberWheel(
                    label = "分钟",
                    values = 0..59,
                    selected = minutes,
                    onSelected = {
                        minutes = it
                        showError = false
                    },
                    modifier = Modifier.weight(1f),
                )
                NumberWheel(
                    label = "秒",
                    values = 0..59,
                    selected = seconds,
                    onSelected = {
                        seconds = it
                        showError = false
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = if (showError) "完赛时间必须大于 00:00:00" else " ",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = {
                    val totalSeconds = hours * 3_600L + minutes * 60L + seconds
                    if (totalSeconds > 0L) {
                        onConfirm(totalSeconds)
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("确定")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    label: String,
    values: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selected - values.first).coerceIn(0, values.count() - 1),
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState, values) {
        snapshotFlow {
            if (listState.isScrollInProgress) {
                null
            } else {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (
                    layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset
                ) / 2
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - viewportCenter)
                }?.index
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index -> onSelected(values.first + index) }
    }

    Column(
        modifier = modifier.semantics {
            contentDescription = "$label，当前 ${selected.toString().padStart(2, '0')}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            ) {}
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = 48.dp),
                flingBehavior = flingBehavior,
            ) {
                itemsIndexed(values.toList()) { index, value ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                onSelected(value)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (value == selected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            modifier = Modifier.alpha(if (value == selected) 1f else 0.55f),
                        )
                    }
                }
            }
        }
    }
}
