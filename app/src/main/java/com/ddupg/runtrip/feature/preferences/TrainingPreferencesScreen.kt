package com.ddupg.runtrip.feature.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.repository.TrainingPreferencesRepository
import com.ddupg.runtrip.ui.components.RunTripControlTheme
import java.math.BigDecimal

@Composable
fun TrainingPreferencesRoute(repository: TrainingPreferencesRepository, onBack: () -> Unit) {
    val model: TrainingPreferencesViewModel = viewModel(factory = TrainingPreferencesViewModel.Factory(repository))
    val state by model.uiState.collectAsStateWithLifecycle()
    TrainingPreferencesScreen(state, onBack, model::edit, model::changeInput, model::dismiss, model::save)
}

private enum class PreferencesHelp { RUNNING_GROWTH, EQUIVALENT_DISTANCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPreferencesScreen(
    state: TrainingPreferencesUiState,
    onBack: () -> Unit,
    onEdit: (TrainingPreferenceField) -> Unit,
    onInput: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var help by rememberSaveable { mutableStateOf<PreferencesHelp?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text("训练偏好", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回我")
            } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)) {
            PreferenceHeading("跑步增长参考") { help = PreferencesHelp.RUNNING_GROWTH }
            PreferenceRow(TrainingPreferenceField.RUNNING_WEEKLY_GROWTH, state.preferences, onEdit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceRow(TrainingPreferenceField.RUNNING_MONTHLY_GROWTH, state.preferences, onEdit)
            PreferenceNote("仅用于跑步统计中的增长参考线")
            Spacer(Modifier.height(28.dp))
            PreferenceHeading("等效跑量换算") { help = PreferencesHelp.EQUIVALENT_DISTANCE }
            PreferenceRow(TrainingPreferenceField.CYCLING_FACTOR, state.preferences, onEdit,
                "1 km 骑行折算 ${trainingPreferenceNumber(state.preferences.cyclingFactor)} km 跑量")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceRow(TrainingPreferenceField.SWIMMING_FACTOR, state.preferences, onEdit,
                "1 km 游泳折算 ${trainingPreferenceNumber(state.preferences.swimmingFactor)} km 跑量")
            PreferenceNote("跑步距离按 1:1 计入")
        }
    }
    state.editing?.let { field ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(field.label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RunTripControlTheme {
                        OutlinedTextField(
                            value = state.input, onValueChange = onInput,
                            label = { Text(if (field.percentage) "百分比" else "换算比例") },
                            suffix = if (field.percentage) ({ Text("%") }) else null,
                            singleLine = true, enabled = !state.saving, isError = state.error != null,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    if (!field.percentage) Text("保存后，全部历史综合统计和增长率会按新比例重新计算。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = onSave, enabled = !state.saving) { Text(if (state.saving) "保存中…" else "保存") } },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !state.saving) { Text("取消") } },
        )
    }
    when (help) {
        PreferencesHelp.RUNNING_GROWTH -> RunningGrowthHelpDialog { help = null }
        PreferencesHelp.EQUIVALENT_DISTANCE -> EquivalentDistanceHelpDialog(state.preferences) { help = null }
        null -> Unit
    }
}

@Composable
private fun PreferenceHeading(label: String, onHelp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onHelp) {
            Icon(Icons.Outlined.HelpOutline, contentDescription = "${label}说明", modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreferenceRow(
    field: TrainingPreferenceField,
    preferences: TrainingPreferences,
    onEdit: (TrainingPreferenceField) -> Unit,
    description: String? = null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onEdit(field) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(2f)) {
            Text(field.label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val value = trainingPreferenceNumber(field.value(preferences))
        Text(if (field.percentage) "$value%" else "×$value", modifier = Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreferenceNote(text: String) {
    Text(text, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RunningGrowthHelpDialog(onDismiss: () -> Unit) {
    PreferenceHelpDialog("跑步增长参考", onDismiss) {
        Text("参考距离 = 上个完整周期的跑步距离 ×（1 + 设定百分比）。")
        Text("例如：上周跑步 30 km，周增幅设为 5%，本周参考距离为 31.5 km。")
        Text("本周或本月尚未结束时，以当前累计距离与上个完整周期比较。")
        Text("这是个人设置的增长参考，不是安全保证；仅用于跑步统计，不用于综合等效跑量。")
    }
}

@Composable
fun EquivalentDistanceHelpDialog(preferences: TrainingPreferences, onDismiss: () -> Unit) {
    val cycling = trainingPreferenceNumber(preferences.cyclingFactor)
    val swimming = trainingPreferenceNumber(preferences.swimmingFactor)
    val example = BigDecimal.valueOf(30).add(BigDecimal.valueOf(preferences.cyclingFactor).multiply(BigDecimal.valueOf(80)))
        .add(BigDecimal.valueOf(preferences.swimmingFactor).multiply(BigDecimal.valueOf(2))).stripTrailingZeros().toPlainString()
    PreferenceHelpDialog("等效跑量怎么算？", onDismiss) {
        Text("将三项运动的距离统一折算为跑量。")
        Text("等效跑量 = 跑步距离\n＋ 骑行距离 × $cycling\n＋ 游泳距离 × $swimming")
        Text("所有距离均按公里计算。")
        Text("例如：跑步 30 km、骑行 80 km、游泳 2 km，合计 $example km。")
        Text("这是统计用的换算规则，不代表相同的训练强度。")
        Text("修改比例后，全部历史综合统计和增长率会按新比例重新计算。")
    }
}

@Composable
private fun PreferenceHelpDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
