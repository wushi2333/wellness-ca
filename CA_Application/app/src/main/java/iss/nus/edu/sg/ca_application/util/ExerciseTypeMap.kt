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

    /** Convert a canonical English key to the localized display text for the given context. */
    fun toDisplay(context: android.content.Context, key: String): String {
        val types = context.resources.getStringArray(iss.nus.edu.sg.ca_application.R.array.exercise_types)
        val idx = keys.indexOfFirst { it.equals(key, ignoreCase = true) }
        if (idx >= 0 && idx < types.size) return types[idx]
        // If it's already a localized value, find its canonical key first, then translate
        val zhIdx = types.indexOfFirst { it.equals(key, ignoreCase = true) }
        if (zhIdx >= 0 && zhIdx < keys.size) return types[zhIdx]
        return key // unknown — display as-is
    }
}
