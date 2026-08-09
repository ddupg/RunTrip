package com.ddupg.runtrip.feature.pace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ddupg.runtrip.ui.components.RunTripControlTheme
import com.ddupg.runtrip.ui.components.RunTripFilterChip
import com.ddupg.runtrip.ui.theme.RunTripTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DISTANCE_INPUT_PATTERN = Regex("""\d*(\.\d*)?""")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaceCalculatorScreen(
    onBack: () -> Unit,
) {
    var selectedPreset by remember { mutableStateOf<PaceDistancePreset?>(null) }
    var customDistance by remember { mutableStateOf("") }
    var finishTimeSeconds by remember { mutableStateOf<Long?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val distanceKm = when (selectedPreset) {
        PaceDistancePreset.CUSTOM -> parseCustomDistance(customDistance)
        null -> null
        else -> selectedPreset?.distanceKm
    }
    val calculation = if (distanceKm != null && finishTimeSeconds != null) {
        calculatePace(distanceKm, finishTimeSeconds = requireNotNull(finishTimeSeconds))
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配速计算", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回工具",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DistanceInput(
                    selectedPreset = selectedPreset,
                    customDistance = customDistance,
                    onSelectPreset = { preset ->
                        selectedPreset = preset
                        focusManager.clearFocus()
                    },
                    onCustomDistanceChange = { value ->
                        if (value.matches(DISTANCE_INPUT_PATTERN)) {
                            customDistance = value
                        }
                    },
                    focusManager = focusManager,
                )
            }
            item {
                TimeInput(
                    finishTimeSeconds = finishTimeSeconds,
                    onClick = {
                        focusManager.clearFocus()
                        showTimePicker = true
                    },
                )
            }
            item {
                if (calculation == null || distanceKm == null || selectedPreset == null) {
                    EmptyCalculation()
                } else {
                    CalculationResult(
                        calculation = calculation,
                        selectedPreset = requireNotNull(selectedPreset),
                        distanceKm = distanceKm,
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        FinishTimePickerSheet(
            initialSeconds = finishTimeSeconds,
            onDismiss = { showTimePicker = false },
            onConfirm = { seconds ->
                finishTimeSeconds = seconds
                showTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DistanceInput(
    selectedPreset: PaceDistancePreset?,
    customDistance: String,
    onSelectPreset: (PaceDistancePreset) -> Unit,
    onCustomDistanceChange: (String) -> Unit,
    focusManager: FocusManager,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "距离",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaceDistancePreset.entries.forEach { preset ->
                RunTripFilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = preset.displayName,
                )
            }
        }
        if (selectedPreset == PaceDistancePreset.CUSTOM) {
            val showError = customDistance.isNotEmpty() && (
                parseCustomDistance(customDistance) == null
            )
            RunTripControlTheme {
                OutlinedTextField(
                    value = customDistance,
                    onValueChange = onCustomDistanceChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义距离") },
                    suffix = { Text("公里") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("请输入大于 0 的公里数") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                )
            }
        }
    }
}

@Composable
private fun TimeInput(
    finishTimeSeconds: Long?,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "完赛时间",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = finishTimeSeconds?.let(::formatElapsedTime) ?: "请选择",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (finishTimeSeconds == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontFamily = if (finishTimeSeconds == null) {
                        FontFamily.Default
                    } else {
                        FontFamily.Monospace
                    },
                    fontWeight = if (finishTimeSeconds == null) {
                        FontWeight.Normal
                    } else {
                        FontWeight.Bold
                    },
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyCalculation() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = "选择距离和完赛时间后显示计算结果",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalculationResult(
    calculation: PaceCalculation,
    selectedPreset: PaceDistancePreset,
    distanceKm: Double,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "平均配速，${
                        formatPaceContentDescription(calculation.paceSecondsPerKm)
                    }"
                },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "平均配速",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatPace(calculation.paceSecondsPerKm),
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "/ 公里",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "分段时间",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SplitTable(
                calculation = calculation,
                selectedPreset = selectedPreset,
                distanceKm = distanceKm,
            )
        }
    }
}

@Composable
private fun SplitTable(
    calculation: PaceCalculation,
    selectedPreset: PaceDistancePreset,
    distanceKm: Double,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            SplitRow(
                distanceLabel = "到达距离",
                timeLabel = "累计时间",
                isHeader = true,
            )
            calculation.splits.forEach { split ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SplitRow(
                    distanceLabel = if (split.isFinish) {
                        val finish = if (selectedPreset == PaceDistancePreset.CUSTOM) {
                            "${formatPaceDistance(distanceKm)} km"
                        } else {
                            selectedPreset.displayName
                        }
                        "终点 · $finish"
                    } else {
                        "${formatPaceDistance(split.distanceKm)} km"
                    },
                    timeLabel = formatElapsedTime(split.elapsedSeconds),
                    isFinish = split.isFinish,
                )
            }
        }
    }
}

@Composable
private fun SplitRow(
    distanceLabel: String,
    timeLabel: String,
    isHeader: Boolean = false,
    isFinish: Boolean = false,
) {
    Surface(
        color = if (isFinish) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (isHeader) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = distanceLabel,
                modifier = Modifier.weight(1f),
                style = if (isHeader) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (isHeader) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isFinish) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = timeLabel,
                style = if (isHeader) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (isHeader) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontFamily = if (isHeader) FontFamily.Default else FontFamily.Monospace,
                fontWeight = if (isHeader) FontWeight.Normal else FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FinishTimePickerSheet(
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

@Preview(showBackground = true)
@Composable
private fun PaceCalculatorScreenPreview() {
    RunTripTheme {
        PaceCalculatorScreen(onBack = {})
    }
}
