package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingSubtype
import com.ddupg.runtrip.data.source.SourceProtocolException
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONObject
import org.json.JSONTokener

internal object CorosTrainingParser {
    val subtypes = mapOf(
        100 to TrainingSubtype.OUTDOOR_RUN, 101 to TrainingSubtype.INDOOR_RUN,
        102 to TrainingSubtype.TRAIL_RUN, 103 to TrainingSubtype.TRACK_RUN,
        200 to TrainingSubtype.OUTDOOR_RIDE, 201 to TrainingSubtype.INDOOR_RIDE,
        202 to TrainingSubtype.E_BIKE, 203 to TrainingSubtype.GRAVEL_RIDE,
        204 to TrainingSubtype.MOUNTAIN_BIKE, 205 to TrainingSubtype.E_MOUNTAIN_BIKE,
        299 to TrainingSubtype.HELMET_RIDE,
        300 to TrainingSubtype.POOL_SWIM, 301 to TrainingSubtype.OPEN_WATER_SWIM,
    )

    fun parse(result: JSONObject, start: LocalDate, end: LocalDate): List<TrainingActivity> {
        if (result.optBoolean("isError")) throw SourceProtocolException()
        val content = result.getJSONArray("content")
        val text = (0 until content.length()).mapNotNull { index ->
            val item = content.getJSONObject(index)
            if (item.optString("type") != "text") null else {
                JSONTokener(item.getString("text")).nextValue() as? String ?: throw SourceProtocolException()
            }
        }.joinToString("\n").replace("\r\n", "\n").trim()
        if (text == "No sport records found from $start to $end.") return emptyList()
        val header = Regex("^Sport Records — $start to $end \\((\\d+) records\\)").find(text)
            ?: throw SourceProtocolException()
        val matches = Regex("(?m)^(\\d+)\\. [^\\n]+ — (\\d{4}-\\d{2}-\\d{2})\\n").findAll(text).toList()
        if (header.groupValues[1].toInt() != matches.size) throw SourceProtocolException()
        val records = matches.mapIndexed { index, match ->
            if (match.groupValues[1].toInt() != index + 1) throw SourceProtocolException()
            val body = text.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: text.length)
            val identity = Regex("LabelId:[ \\t]*(\\S+)[ \\t]*\\|[ \\t]*SportType:[ \\t]*(\\d+)\\b").find(body)
                ?: throw SourceProtocolException()
            val subtype = subtypes[identity.groupValues[2].toInt()] ?: throw SourceProtocolException()
            val date = LocalDate.parse(match.groupValues[2])
            if (date < start || date > end) throw SourceProtocolException()
            val duration = Regex("Duration:[ \\t]*(\\d+:\\d{2}(?::\\d{2})?)(?=[ \\t]*[|\\n]|$)").find(body)
                ?: throw SourceProtocolException()
            val parts = duration.groupValues[1].split(':').map(String::toLong)
            if (parts.drop(1).any { it !in 0..59 }) throw SourceProtocolException()
            val seconds = parts.fold(0L) { total, part -> Math.addExact(Math.multiplyExact(total, 60), part) }
            val distance = Regex("Distance:[ \\t]*(\\d+(?:\\.\\d+)?) (km|m)(?=[ \\t]*[|\\n]|$)").find(body)
            if (distance == null && body.contains("Distance:")) throw SourceProtocolException()
            val meters = distance?.let { it.groupValues[1].toDouble() * if (it.groupValues[2] == "km") 1000 else 1 }
            if (meters != null && !meters.isFinite()) throw SourceProtocolException()
            val timestamp = Regex("startTimestamp=(\\d+)\\b").find(body)?.groupValues?.get(1)?.toLongOrNull()
            if (timestamp == null && body.contains("startTimestamp=")) throw SourceProtocolException()
            if (timestamp != null) {
                // Limit Unix seconds to 1970 onwards and to a four-digit year in any display time zone.
                val latestExclusive = LocalDate.of(9999, 12, 31).plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.MAX)
                if (timestamp !in 0 until latestExclusive) throw SourceProtocolException()
            }
            TrainingActivity("${identity.groupValues[2]}:${identity.groupValues[1]}", subtype, date, timestamp, meters, seconds)
        }
        if (records.map { it.id }.toSet().size != records.size) throw SourceProtocolException()
        return records
    }
}
