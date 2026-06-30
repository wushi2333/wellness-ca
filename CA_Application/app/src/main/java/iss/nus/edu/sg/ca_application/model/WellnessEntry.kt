package iss.nus.edu.sg.ca_application.model

/** Request body for POST /records and PUT /records/{id}. */
data class WellnessEntry(
    val sleepHours: Double,
    val exerciseActivity: String,
    val exerciseDuration: Int,
    val recordDate: String,
    val notes: String
)
