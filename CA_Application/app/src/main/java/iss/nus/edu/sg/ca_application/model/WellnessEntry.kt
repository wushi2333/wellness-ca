package iss.nus.edu.sg.ca_application.model

/**
 * Author: Wang Songyu
 *
 * Represents the request body used for creating or updating
 * a wellness record through the backend REST API.
 *
 * Used by:
 * - POST /records
 * - PUT /records/{id}
 *
 * This model matches the FastAPI WellnessEntry schema.
 * Unlike WellnessRecord, it does not contain an ID because
 * the record ID is generated and managed by the server.
 */
data class WellnessEntry(

    // Hours of sleep
    val sleepHours: Double,

    // Bedtime (HH:mm)
    val sleepTime: String = "",

    // Wake time (HH:mm)
    val wakeTime: String = "",

    // Exercise activity (e.g. Running, Swimming)
    val exerciseActivity: String,

    // Exercise duration in minutes
    val exerciseDuration: Int,

    // Mood score (1-5)
    val moodScore: Int = 0,

    // Record date (yyyy-MM-dd)
    val recordDate: String,

    // Additional notes
    val notes: String

)