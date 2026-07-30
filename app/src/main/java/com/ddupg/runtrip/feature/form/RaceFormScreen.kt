package com.ddupg.runtrip.feature.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.repository.RaceRepository
import com.ddupg.runtrip.ui.components.RunTripControlTheme
import com.ddupg.runtrip.ui.components.RunTripFilterChip
import com.ddupg.runtrip.ui.components.runTripConfirmButtonColors
import com.ddupg.runtrip.ui.components.runTripDatePickerColors
import com.ddupg.runtrip.ui.components.runTripTextButtonColors
import com.ddupg.runtrip.ui.theme.RunTripTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun RaceFormRoute(
    repository: RaceRepository,
    raceId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val factory = remember(repository, raceId) { RaceFormViewModel.Factory(repository, raceId) }
    val viewModel: RaceFormViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect { onSaved() }
    }

    RaceFormScreen(
        uiState = uiState,
        isEditing = raceId != null,
        onBack = onBack,
        onDraftChange = viewModel::updateDraft,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaceFormScreen(
    uiState: RaceFormUiState,
    isEditing: Boolean,
    onBack: () -> Unit,
    onDraftChange: (RaceDraft) -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val draft = uiState.draft

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑比赛" else "添加比赛") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!uiState.isLoading && uiState.loadError == null) {
                        IconButton(
                            onClick = onSave,
                            enabled = !uiState.isSaving,
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Outlined.Check, contentDescription = "保存")
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.loadError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiState.loadError)
                }
            }

            else -> {
                RaceFormContent(
                    modifier = Modifier.padding(innerPadding),
                    uiState = uiState,
                    onDraftChange = onDraftChange,
                    onOpenDatePicker = { showDatePicker = true },
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.raceDate.toUtcMillis(),
        )
        val datePickerColors = runTripDatePickerColors()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = datePickerColors,
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            onDraftChange(
                                draft.copy(
                                    raceDate = Instant.ofEpochMilli(selectedMillis)
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate(),
                                ),
                            )
                        }
                        showDatePicker = false
                    },
                    colors = runTripConfirmButtonColors(),
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    colors = runTripTextButtonColors(),
                ) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                colors = datePickerColors,
            )
        }
    }
}

@Composable
private fun RaceFormContent(
    uiState: RaceFormUiState,
    onDraftChange: (RaceDraft) -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = uiState.draft

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { FormSectionTitle("比赛信息", "必填") }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(name = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("比赛名称") },
                    singleLine = true,
                    isError = uiState.errors.name != null,
                    supportingText = uiState.errors.name?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
        }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.city,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(city = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("城市") },
                    singleLine = true,
                    isError = uiState.errors.city != null,
                    supportingText = uiState.errors.city?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
        }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.raceDate.toChineseDate(),
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDatePicker),
                    readOnly = true,
                    label = { Text("比赛日期") },
                    trailingIcon = {
                        IconButton(onClick = onOpenDatePicker) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "选择比赛日期")
                        }
                    },
                )
            }
        }
        item {
            ChoiceChips(
                label = "比赛项目",
                values = RaceCategory.entries,
                selected = draft.category,
                key = RaceCategory::code,
                displayName = RaceCategory::displayName,
                onSelected = { value ->
                    onDraftChange(draft.copy(category = value))
                },
            )
        }
        item {
            ChoiceChips(
                label = "参赛状态",
                values = RaceStatus.entries,
                selected = draft.status,
                key = RaceStatus::code,
                displayName = RaceStatus::displayName,
                onSelected = { value ->
                    onDraftChange(draft.copy(status = value))
                },
            )
        }

        item { SectionDivider() }
        item { FormSectionTitle("赛事等级", "选填") }
        item {
            OptionalChoiceChips(
                label = "中国田协等级",
                values = CaaRaceLevel.entries,
                selected = draft.caaRaceLevel,
                key = CaaRaceLevel::code,
                displayName = CaaRaceLevel::displayName,
                onSelected = { value ->
                    onDraftChange(draft.copy(caaRaceLevel = value))
                },
            )
        }
        item {
            OptionalChoiceChips(
                label = "World Athletics Label",
                values = WorldAthleticsLabel.entries,
                selected = draft.worldAthleticsLabel,
                key = WorldAthleticsLabel::code,
                displayName = WorldAthleticsLabel::bilingualDisplayName,
                onSelected = { value ->
                    onDraftChange(draft.copy(worldAthleticsLabel = value))
                },
            )
        }

        item { SectionDivider() }
        item { FormSectionTitle("路程", "选填") }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.travelDistance,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(travelDistance = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("路程距离") },
                    suffix = { Text("km") },
                    singleLine = true,
                    isError = uiState.errors.travelDistance != null,
                    supportingText = uiState.errors.travelDistance?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
        }

        item { SectionDivider() }
        item { FormSectionTitle("住宿", "选填") }
        item {
            ChoiceChips(
                label = "预订状态",
                values = HotelBookingStatus.entries,
                selected = draft.hotelBookingStatus,
                key = HotelBookingStatus::code,
                displayName = HotelBookingStatus::displayName,
                onSelected = { value ->
                    onDraftChange(draft.copy(hotelBookingStatus = value))
                },
            )
        }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.hotelName,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(hotelName = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("酒店名称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
        }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.bookingPlatform,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(bookingPlatform = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("预订平台") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
        }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.hotelPrice,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(hotelPrice = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("酒店总价") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    isError = uiState.errors.hotelPrice != null,
                    supportingText = uiState.errors.hotelPrice?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
        }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.hotelNotes,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(hotelNotes = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("酒店备注") },
                    minLines = 3,
                )
            }
        }

        item { SectionDivider() }
        item { FormSectionTitle("比赛备注", "选填") }
        item {
            RunTripControlTheme {
                OutlinedTextField(
                    value = draft.raceNotes,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(raceNotes = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    minLines = 4,
                )
            }
        }

        uiState.saveError?.let { error ->
            item {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FormSectionTitle(title: String, hint: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionDivider() {
    Column {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun <T> ChoiceChips(
    label: String,
    values: List<T>,
    selected: T,
    key: (T) -> String,
    displayName: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = key) { value ->
                RunTripFilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = displayName(value),
                )
            }
        }
    }
}

@Composable
private fun <T> OptionalChoiceChips(
    label: String,
    values: List<T>,
    selected: T?,
    key: (T) -> String,
    displayName: (T) -> String,
    onSelected: (T?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                RunTripFilterChip(
                    selected = selected == null,
                    onClick = { onSelected(null) },
                    label = "未填写",
                )
            }
            items(values, key = key) { value ->
                RunTripFilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = displayName(value),
                )
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun LocalDate.toChineseDate(): String =
    "$year 年 ${monthValue.toString().padStart(2, '0')} 月 " +
        "${dayOfMonth.toString().padStart(2, '0')} 日"

@Preview(showBackground = true)
@Composable
private fun RaceFormScreenPreview() {
    RunTripTheme {
        RaceFormScreen(
            uiState = RaceFormUiState(),
            isEditing = false,
            onBack = {},
            onDraftChange = {},
            onSave = {},
        )
    }
}
