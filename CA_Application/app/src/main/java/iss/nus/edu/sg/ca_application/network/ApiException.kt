package iss.nus.edu.sg.ca_application.network

/** Thrown when the backend returns an HTTP error (4xx or 5xx). */
class ApiException(
    val code: Int,
    val body: String
) : Exception("HTTP $code: $body")
