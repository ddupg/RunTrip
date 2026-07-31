package com.ddupg.runtrip.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.ui.presentation.RacePresentation

internal enum class RaceStatusBadgeStyle {
    CONFIRMED,
    WON,
    PENDING,
    MUTED,
    FINISHED,
}

internal enum class RaceStatusSymbol {
    EYE,
    EDIT,
    HOURGLASS,
    STAR,
    MINUS_CIRCLE,
    CHECK_CIRCLE,
    CANCEL,
    FLAG,
}

internal data class RaceStatusVisualSpec(
    val style: RaceStatusBadgeStyle,
    val symbol: RaceStatusSymbol,
)

internal fun RaceStatus.visualSpec(): RaceStatusVisualSpec = when (this) {
    RaceStatus.WATCHING -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.PENDING,
        RaceStatusSymbol.EYE,
    )

    RaceStatus.REGISTRATION_PENDING -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.PENDING,
        RaceStatusSymbol.EDIT,
    )

    RaceStatus.DRAW_PENDING -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.PENDING,
        RaceStatusSymbol.HOURGLASS,
    )

    RaceStatus.DRAW_WON -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.WON,
        RaceStatusSymbol.STAR,
    )

    RaceStatus.DRAW_LOST -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.MUTED,
        RaceStatusSymbol.MINUS_CIRCLE,
    )

    RaceStatus.REGISTERED -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.CONFIRMED,
        RaceStatusSymbol.CHECK_CIRCLE,
    )

    RaceStatus.WITHDRAWN -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.MUTED,
        RaceStatusSymbol.CANCEL,
    )

    RaceStatus.FINISHED -> RaceStatusVisualSpec(
        RaceStatusBadgeStyle.FINISHED,
        RaceStatusSymbol.FLAG,
    )
}

internal data class RaceStatusBadgeColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color? = null,
)

internal fun ColorScheme.raceStatusBadgeColors(
    style: RaceStatusBadgeStyle,
): RaceStatusBadgeColors {
    val darkTheme = background.luminance() < 0.5f
    return when (style) {
        RaceStatusBadgeStyle.CONFIRMED -> RaceStatusBadgeColors(
            containerColor = primary,
            contentColor = onPrimary,
        )

        RaceStatusBadgeStyle.WON -> if (darkTheme) {
            RaceStatusBadgeColors(
                containerColor = secondaryContainer,
                contentColor = onSecondaryContainer,
                borderColor = primary,
            )
        } else {
            RaceStatusBadgeColors(
                containerColor = secondary,
                contentColor = onSecondary,
            )
        }

        RaceStatusBadgeStyle.PENDING -> RaceStatusBadgeColors(
            containerColor = Color.Transparent,
            contentColor = if (darkTheme) onSurface else secondary,
            borderColor = outline,
        )

        RaceStatusBadgeStyle.MUTED -> RaceStatusBadgeColors(
            containerColor = Color.Transparent,
            contentColor = onSurfaceVariant,
        )

        RaceStatusBadgeStyle.FINISHED -> RaceStatusBadgeColors(
            containerColor = Color.Transparent,
            contentColor = onSurfaceVariant,
            borderColor = outline,
        )
    }
}

@Composable
internal fun RunTripRaceStatusBadge(
    status: RaceStatus,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    onClick: (() -> Unit)? = null,
) {
    val spec = status.visualSpec()
    val colors = MaterialTheme.colorScheme.raceStatusBadgeColors(spec.style)
    val border = colors.borderColor?.let { BorderStroke(1.dp, it) }
    val content = @Composable {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RaceStatusIcon(
                status = status,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = RacePresentation.status(status).text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = colors.containerColor,
            contentColor = colors.contentColor,
            border = border,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            color = colors.containerColor,
            contentColor = colors.contentColor,
            border = border,
            content = content,
        )
    }
}

@Composable
internal fun RaceStatusIcon(
    status: RaceStatus,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = status.visualSpec().symbol.imageVector(),
        contentDescription = null,
        modifier = modifier,
    )
}

private fun RaceStatusSymbol.imageVector(): ImageVector = when (this) {
    RaceStatusSymbol.EYE -> Icons.Outlined.Visibility
    RaceStatusSymbol.EDIT -> Icons.Outlined.EditNote
    RaceStatusSymbol.HOURGLASS -> Icons.Outlined.HourglassEmpty
    RaceStatusSymbol.STAR -> Icons.Outlined.StarOutline
    RaceStatusSymbol.MINUS_CIRCLE -> Icons.Outlined.RemoveCircleOutline
    RaceStatusSymbol.CHECK_CIRCLE -> Icons.Outlined.CheckCircleOutline
    RaceStatusSymbol.CANCEL -> Icons.Outlined.Cancel
    RaceStatusSymbol.FLAG -> Icons.Outlined.Flag
}
