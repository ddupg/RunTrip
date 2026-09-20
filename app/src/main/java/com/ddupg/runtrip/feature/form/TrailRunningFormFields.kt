package com.ddupg.runtrip.feature.form

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.ddupg.runtrip.ui.components.RunTripControlTheme

internal fun LazyListScope.trailRunningFields(
    draft: TrailRunningDraft,
    errors: Map<String, String>,
    onChange: (TrailRunningDraft) -> Unit,
) {
    item { SectionDivider() }
    item { FormSectionTitle("越野信息", "比赛距离必填") }
    item {
        RunTripControlTheme {
            OutlinedTextField(
                value = draft.distance,
                onValueChange = { onChange(draft.copy(distance = it)) },
                label = { Text("比赛距离") },
                suffix = { Text("km") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errors[TrailRunningDraft.DISTANCE] != null,
                supportingText = errors[TrailRunningDraft.DISTANCE]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
        }
    }
    item {
        RunTripControlTheme {
            OutlinedTextField(
                value = draft.elevationGain,
                onValueChange = { onChange(draft.copy(elevationGain = it)) },
                label = { Text("累计爬升（选填）") },
                suffix = { Text("m") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errors[TrailRunningDraft.ELEVATION_GAIN] != null,
                supportingText = errors[TrailRunningDraft.ELEVATION_GAIN]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
        }
    }
}
