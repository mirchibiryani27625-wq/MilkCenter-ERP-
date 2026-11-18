package com.example.util

import com.example.data.model.StationConfig
import java.util.Locale
import java.util.regex.Pattern

/**
 * Result of classifying Morning / Evening shift strictly from receipt/machine time
 * or explicit printed shift label — NEVER from the device's system clock.
 */
data class ShiftClassificationResult(
    val shift: String?, // "MORNING", "EVENING", or null if unreadable/ambiguous
    val confidence: Int,
    val normalizedTime24h: String? = null,
    val note: String? = null,
    val fromExplicitLabel: Boolean = false
)

object ShiftTimingClassifier {

    /**
     * Parses time strings like "07:45 AM", "12:15 PM", "06:20:10 pm", "18:30", "7.45 AM"
     * into minutes since midnight [0..1439], or null if unreadable.
     */
    fun parseTimeToMinutes(rawTime: String?): Int? {
        if (rawTime.isNullOrBlank()) return null
        val matcher = Pattern.compile(
            """(\d{1,2})[:.](\d{2})(?:[:.](\d{2}))?\s*([AaPp][Mm])?"""
        ).matcher(rawTime.trim())
        if (!matcher.find()) return null

        val rawHour = matcher.group(1)?.toIntOrNull() ?: return null
        val minute = matcher.group(2)?.toIntOrNull() ?: return null
        val amPm = matcher.group(4)?.uppercase(Locale.US)

        if (minute !in 0..59) return null

        val hour24 = when {
            amPm == "AM" -> {
                if (rawHour !in 1..12) return null
                if (rawHour == 12) 0 else rawHour
            }
            amPm == "PM" -> {
                if (rawHour !in 1..12) return null
                if (rawHour == 12) 12 else rawHour + 12
            }
            else -> {
                if (rawHour !in 0..23) return null
                rawHour
            }
        }
        return hour24 * 60 + minute
    }

    /**
     * Classifies shift strictly from extracted time using Admin-configured Morning & Evening timing windows
     * (Default: Morning 04:00 AM–11:59 AM, Evening 12:00 PM–11:59 PM).
     * If time is missing or outside windows, falls back to explicit printed label ("MORNING"/"EVENING"/"M"/"E"),
     * or returns null so operator must choose manually. NEVER guesses from phone time.
     */
    fun classifyShift(
        extractedTime: String?,
        explicitShiftLabel: String? = null,
        config: StationConfig = StationConfig()
    ): ShiftClassificationResult {
        val explicitNorm = normalizeExplicitShift(explicitShiftLabel)
        val minutes = parseTimeToMinutes(extractedTime)

        if (minutes != null) {
            val morningStart = config.morningStartHour * 60 + config.morningStartMinute
            val morningEnd = config.morningEndHour * 60 + config.morningEndMinute
            val eveningStart = config.eveningStartHour * 60 + config.eveningStartMinute
            val eveningEnd = config.eveningEndHour * 60 + config.eveningEndMinute

            val formatted24 = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
            val timeShift = when {
                isInRange(minutes, morningStart, morningEnd) -> "MORNING"
                isInRange(minutes, eveningStart, eveningEnd) -> "EVENING"
                else -> null
            }

            if (timeShift != null) {
                // Check for conflict with explicit label if both exist
                return if (explicitNorm != null && explicitNorm != timeShift) {
                    ShiftClassificationResult(
                        shift = timeShift,
                        confidence = 75,
                        normalizedTime24h = formatted24,
                        note = "समय ($extractedTime → $timeShift) और मुद्रित शिफ्ट ($explicitNorm) में अंतर है — कृपया जांचें",
                        fromExplicitLabel = false
                    )
                } else {
                    ShiftClassificationResult(
                        shift = timeShift,
                        confidence = if (explicitNorm == timeShift) 98 else 94,
                        normalizedTime24h = formatted24,
                        fromExplicitLabel = false
                    )
                }
            } else if (explicitNorm != null) {
                return ShiftClassificationResult(
                    shift = explicitNorm,
                    confidence = 84,
                    normalizedTime24h = formatted24,
                    note = "समय $extractedTime निर्धारित शिफ्ट समय सीमा से बाहर है; मुद्रित शिफ्ट $explicitNorm लागू",
                    fromExplicitLabel = true
                )
            } else {
                return ShiftClassificationResult(
                    shift = null,
                    confidence = 25,
                    normalizedTime24h = formatted24,
                    note = "समय $extractedTime निर्धारित सुबह (${formatWindow(config.morningStartHour, config.morningStartMinute)}–${formatWindow(config.morningEndHour, config.morningEndMinute)}) व शाम (${formatWindow(config.eveningStartHour, config.eveningStartMinute)}–${formatWindow(config.eveningEndHour, config.eveningEndMinute)}) सीमा से बाहर है — ऑपरेटर चयन आवश्यक"
                )
            }
        }

        if (explicitNorm != null) {
            return ShiftClassificationResult(
                shift = explicitNorm,
                confidence = 92,
                fromExplicitLabel = true
            )
        }

        return ShiftClassificationResult(
            shift = null,
            confidence = 0,
            note = "रसीद/मशीन पर समय व शिफ्ट स्पष्ट नहीं है — कृपया सुबह या शाम चुनें"
        )
    }

    fun normalizeExplicitShift(rawLabel: String?): String? {
        val s = rawLabel?.trim()?.uppercase(Locale.US) ?: return null
        return when {
            s == "M" || s == "MOR" || s == "MORNING" || s == "AM" || s.contains("सुबह") -> "MORNING"
            s == "E" || s == "EVE" || s == "EVENING" || s == "PM" || s.contains("शाम") -> "EVENING"
            else -> null
        }
    }

    private fun isInRange(minutes: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            minutes in start..end
        } else {
            minutes >= start || minutes <= end
        }
    }

    fun formatWindow(hour24: Int, minute: Int): String {
        val period = if (hour24 < 12) "AM" else "PM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%02d:%02d %s", h12, minute, period)
    }
}
