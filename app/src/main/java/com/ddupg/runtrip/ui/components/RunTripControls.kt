package com.ddupg.runtrip.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun RunTripControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.withRunTripControlColors(),
        content = content,
    )
}

internal fun ColorScheme.withRunTripControlColors(): ColorScheme = copy(
    primary = secondary,
    onPrimary = onSecondary,
    primaryContainer = secondaryContainer,
    onPrimaryContainer = onSecondaryContainer,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunTripFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = colors.onSurface,
            selectedContainerColor = colors.secondaryContainer,
            selectedLabelColor = colors.onSecondaryContainer,
            selectedLeadingIconColor = colors.tertiary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.outline,
            selectedBorderColor = colors.tertiary,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun runTripDatePickerColors(): DatePickerColors {
    val colors = MaterialTheme.colorScheme
    return DatePickerDefaults.colors(
        containerColor = colors.surface,
        titleContentColor = colors.onSurfaceVariant,
        headlineContentColor = colors.onSurface,
        weekdayContentColor = colors.onSurfaceVariant,
        subheadContentColor = colors.onSurface,
        navigationContentColor = colors.onSurface,
        yearContentColor = colors.onSurface,
        currentYearContentColor = colors.secondary,
        selectedYearContentColor = colors.onSecondary,
        selectedYearContainerColor = colors.secondary,
        dayContentColor = colors.onSurface,
        selectedDayContentColor = colors.onSecondary,
        selectedDayContainerColor = colors.secondary,
        todayContentColor = colors.secondary,
        todayDateBorderColor = colors.secondary,
        dividerColor = colors.outlineVariant,
    )
}

@Composable
internal fun runTripTextButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.secondary,
    )

@Composable
internal fun runTripConfirmButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    )
