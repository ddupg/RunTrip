package com.ddupg.runtrip.ui.presentation

import com.ddupg.runtrip.data.model.TriathlonCategory

internal object TriathlonPresentation {
    fun category(category: TriathlonCategory): String = when (category) {
        TriathlonCategory.SPRINT -> "半标"
        TriathlonCategory.STANDARD -> "全标"
        TriathlonCategory.OTHER -> "其他"
    }
}
