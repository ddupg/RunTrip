package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonCategory
import com.ddupg.runtrip.data.model.TriathlonDetails

data class TriathlonDraft(override val category: TriathlonCategory? = null) : SportDraft {
    override val sportType: SportType get() = SportType.TRIATHLON
    override fun withCategory(category: RaceCategory): TriathlonDraft {
        require(category is TriathlonCategory)
        return copy(category = category)
    }

    override fun toDetails(): TriathlonDetails? = category?.let(::TriathlonDetails)
}
