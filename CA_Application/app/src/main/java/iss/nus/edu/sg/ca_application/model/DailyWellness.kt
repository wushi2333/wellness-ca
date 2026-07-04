// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.model

/**
 * Aggregated daily wellness data returned by GET /records.
 * One entry per date with optional sleep and a list of exercise entries.
 */
data class DailyWellness(
    val dailyRecordId: Long,
    val recordDate: String,
    val sleep: SleepRecord?,
    val exercises: List<ExerciseRecord>
)

/** Sleep sub-record within a [DailyWellness]. */
data class SleepRecord(
    val id: Long,
    val sleepHours: Double,
    val sleepTime: String,
    val wakeTime: String,
    val moodScore: Int,
    val notes: String
)

/** Exercise sub-record within a [DailyWellness]. */
data class ExerciseRecord(
    val id: Long,
    val exerciseActivity: String,
    val exerciseDuration: Int,
    val notes: String
)
