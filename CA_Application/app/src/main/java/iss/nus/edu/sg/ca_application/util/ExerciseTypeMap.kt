// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.util

/**
 * Maps exercise activity names between canonical English keys and localized display text.
 * All records are stored with English keys; UI displays the localized version.
 */
object ExerciseTypeMap {

    // Canonical English key — index matches the exercise_types string-array
    private val keys = listOf(
        "Running", "Cycling", "Swimming", "Yoga", "Strength Training",
        "Cardio", "Walking", "HIIT", "Pilates", "Other"
    )

    /** Convert any display text (EN or ZH) to the canonical English key. */
    fun toKey(display: String): String {
        // Direct match against English keys
        val enIndex = keys.indexOfFirst { it.equals(display, ignoreCase = true) }
        if (enIndex >= 0) return keys[enIndex]
        return display // unknown — store as-is
    }

    /** Same as toKey() but also checks Chinese names against resources. */
    fun toKey(context: android.content.Context, display: String): String {
        // Direct English match
        val enIndex = keys.indexOfFirst { it.equals(display, ignoreCase = true) }
        if (enIndex >= 0) return keys[enIndex]
        // Check Chinese names via string-array index
        val zhTypes = context.resources.getStringArray(iss.nus.edu.sg.ca_application.R.array.exercise_types)
        val zhIdx = zhTypes.indexOfFirst { it.equals(display, ignoreCase = true) }
        if (zhIdx >= 0 && zhIdx < keys.size) return keys[zhIdx]
        return display
    }

    /** Locale-independent Chinese→English reverse map. */
    private val reverseMap = mapOf(
        "跑步" to "Running", "骑行" to "Cycling", "游泳" to "Swimming",
        "瑜伽" to "Yoga", "力量训练" to "Strength Training", "有氧运动" to "Cardio",
        "步行" to "Walking", "普拉提" to "Pilates", "其他" to "Other"
    )

    /** Convert any stored key (EN or ZH) to the localized display text. */
    fun toDisplay(context: android.content.Context, key: String): String {
        // Normalize: Chinese name → English key
        val normalized = reverseMap[key] ?: key
        val types = context.resources.getStringArray(iss.nus.edu.sg.ca_application.R.array.exercise_types)
        val idx = keys.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        if (idx >= 0 && idx < types.size) return types[idx]
        return normalized
    }
}
