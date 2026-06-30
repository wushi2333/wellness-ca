package iss.nus.edu.sg.ca_application.model

/** Response item for GET /records. */
data class WellnessRecord(
    val id: Int = 0,
    val sleepHours: Double,
    val exerciseActivity: String,
    val exerciseDuration: Int,
    val recordDate: String,
    val notes: String
)
