package iss.nus.edu.sg.ca_application.auth

/**
 * Login screen — first Activity the user sees.
 *
 * Flow:
 *   1. User enters username and password
 *   2. App sends POST /login with JSON body { "username": "...", "password": "..." }
 *   3. On success (200): store JWT via TokenManager → navigate to MainActivity
 *   4. On failure (400): show error Toast/Dialog
 *   5. On 401 from any subsequent request: clear token → return to this screen
 *
 * Layout: res/layout/activity_login.xml
 *
 * Dependencies:
 *   network.ApiClient       — for HTTP calls
 *   auth.TokenManager       — for JWT persistence
 *   model.LoginRequest      — request body data class
 *   model.LoginResponse     — response body data class
 *
 * Course references:
 *   02_Layouts_n_Resources.pdf / 03_UI_Controls.pdf  — UI design
 *   4.0 View Binding.pdf                              — view binding
 *   04_Activity_n_Intent.pdf                           — navigation intent
 *   06_Threads.pdf                                     — background threads
 *   07_Image_Download.pdf                              — HttpURLConnection pattern
 *   4 JWT.pptx                                         — JWT concept
 */
