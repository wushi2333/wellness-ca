// Author: Wang Songyu, Xia Zihang
package iss.nus.edu.sg.ca_application.network

/**
 * Author: Wang Songyu
 *
 * Custom exception used for backend API requests.
 *
 * This exception is thrown whenever the server returns
 * an HTTP error response (4xx or 5xx).
 *
 * The exception contains:
 * - HTTP status code
 * - Response body returned by the server
 */
class ApiException(
    val code: Int,
    val body: String
) : Exception("HTTP $code: $body")