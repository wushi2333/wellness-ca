// Author: Xia Zihang
package iss.nus.edu.sg.ca_application

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.model.UserProfileData
import iss.nus.edu.sg.ca_application.network.ApiErrorHandler
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.BASE_URL
import iss.nus.edu.sg.ca_application.network.ProfileApi
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(SettingsActivity.wrapContextForLocale(newBase))
    }

    private lateinit var ivAvatar: ImageView
    private lateinit var tvChangePhoto: TextView
    private lateinit var tvUsername: TextView
    private lateinit var btnEditUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var btnEditEmail: TextView
    private lateinit var tvProvider: TextView
    // Body metrics – display mode (read-only grey text)
    private lateinit var tvHeight: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvAge: TextView
    // Body metrics – edit mode (TextInputLayout wrappers + EditTexts)
    private lateinit var tilHeight: View
    private lateinit var tilWeight: View
    private lateinit var tilAge: View
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var etAge: EditText
    private lateinit var btnEditMetrics: View
    private lateinit var btnSaveMetrics: View

    private var profileData: UserProfileData? = null
    private var isMetricsEditing = false
    // Original metric values loaded from server (null = no data)
    private var origHeight: Int? = null
    private var origWeight: Double? = null
    private var origAge: Int? = null

    // Step 1: pick an image from gallery
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { launchCrop(it) }
    }

    // Step 2: circular crop result from CircleCropActivity
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val croppedUriStr = result.data?.getStringExtra("cropped_uri")
            if (croppedUriStr != null) {
                handleCroppedImage(Uri.parse(croppedUriStr))
            }
        }
    }

    private fun launchCrop(sourceUri: Uri) {
        val intent = Intent(this, CircleCropActivity::class.java).apply {
            putExtra("image_uri", sourceUri.toString())
        }
        cropLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_profile)

        ivAvatar = findViewById(R.id.ivAvatar)
        // Clip avatar to circle
        ivAvatar.clipToOutline = true
        ivAvatar.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val size = minOf(view.width, view.height)
                val left = (view.width - size) / 2
                val top = (view.height - size) / 2
                outline.setOval(left, top, left + size, top + size)
            }
        }
        tvChangePhoto = findViewById(R.id.tvChangePhoto)
        tvUsername = findViewById(R.id.tvUsername)
        btnEditUsername = findViewById(R.id.btnEditUsername)
        tvEmail = findViewById(R.id.tvEmail)
        btnEditEmail = findViewById(R.id.btnEditEmail)
        tvProvider = findViewById(R.id.tvProvider)
        tvHeight = findViewById(R.id.tvHeight)
        tvWeight = findViewById(R.id.tvWeight)
        tvAge = findViewById(R.id.tvAge)
        tilHeight = findViewById(R.id.tilHeight)
        tilWeight = findViewById(R.id.tilWeight)
        tilAge = findViewById(R.id.tilAge)
        etHeight = findViewById(R.id.etHeight)
        etWeight = findViewById(R.id.etWeight)
        etAge = findViewById(R.id.etAge)
        btnEditMetrics = findViewById(R.id.btnEditMetrics)
        btnSaveMetrics = findViewById(R.id.btnSaveMetrics)

        (findViewById<View>(R.id.btnBack).parent as View).applyTopInset()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        ivAvatar.setOnClickListener { imagePickerLauncher.launch("image/*") }
        tvChangePhoto.setOnClickListener { imagePickerLauncher.launch("image/*") }

        btnEditUsername.setOnClickListener { showEditUsernameDialog() }
        btnEditEmail.setOnClickListener { showEditEmailDialog() }
        btnEditMetrics.setOnClickListener { toggleMetricsEdit() }
        btnSaveMetrics.setOnClickListener { saveMetrics() }

        loadProfile()
    }

    private fun loadProfile() {
        val token = TokenManager.getToken(this)
        Thread {
            try {
                val data = ProfileApi.getProfile(token)
                profileData = data
                runOnUiThread { populateUI(data) }
            } catch (e: ApiException) {
                runOnUiThread {
                    if (!ApiErrorHandler.handle(this, e)) {
                        Toast.makeText(this, R.string.network_error, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { ApiErrorHandler.handleGeneric(this, e) }
            }
        }.start()
    }

    private fun populateUI(data: UserProfileData) {
        val isGoogle = data.provider == "GOOGLE"
        tvUsername.text = data.username
        // Google users cannot change email — show locked
        if (isGoogle) {
            tvEmail.text = data.email ?: getString(R.string.not_set)
            btnEditEmail.visibility = View.GONE
        } else if (data.email.isNullOrEmpty()) {
            tvEmail.text = getString(R.string.not_set)
            btnEditEmail.text = getString(R.string.bind_email)
            btnEditEmail.visibility = View.VISIBLE
        } else {
            tvEmail.text = data.email
            btnEditEmail.text = getString(R.string.change_email)
            btnEditEmail.visibility = View.VISIBLE
        }
        tvProvider.text = if (isGoogle) getString(R.string.provider_google) else getString(R.string.provider_local)

        // Body metrics – save originals and show display values
        origHeight = data.heightCm
        origWeight = data.weightKg
        origAge = data.age
        tvHeight.text = data.heightCm?.let { "$it cm" } ?: getString(R.string.not_set)
        tvWeight.text = data.weightKg?.let { "$it kg" } ?: getString(R.string.not_set)
        tvAge.text = data.age?.toString() ?: getString(R.string.not_set)
        // Pre-fill edit fields for later use
        data.heightCm?.let { etHeight.setText(it.toString()) }
        data.weightKg?.let { etWeight.setText(it.toString()) }
        data.age?.let { etAge.setText(it.toString()) }

        if (!data.avatarUrl.isNullOrEmpty()) {
            loadAvatar(data.avatarUrl)
        }
    }

    private fun loadAvatar(avatarUrl: String) {
        Thread {
            try {
                val fullUrl = if (avatarUrl.startsWith("http")) avatarUrl else "$BASE_URL$avatarUrl"
                val url = URL(fullUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doInput = true
                conn.connect()
                if (conn.responseCode == 200) {
                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    runOnUiThread { ivAvatar.setImageBitmap(bmp) }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }

    /** Called after user crops their image via the circular crop UI. Compresses to JPEG. */
    private fun handleCroppedImage(uri: Uri) {
        try {
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap != null) {
                // Resize if too large (max 512px on longest side)
                val maxDim = 512
                if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                }

                // Flatten to white background (corners invisible due to circular clip)
                val flattened = CircleCropActivity.flattenToWhiteBg(bitmap)
                ivAvatar.setImageBitmap(flattened)

                // Compress JPEG ~85% quality → small file size
                val bos = ByteArrayOutputStream()
                flattened.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                uploadAvatar(bos.toByteArray(), "avatar.jpg")
                bos.close()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAvatar(imageBytes: ByteArray, fileName: String = "avatar.png") {
        val token = TokenManager.getToken(this)
        Thread {
            try {
                val avatarUrl = ProfileApi.uploadAvatar(token, imageBytes, fileName)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    if (!ApiErrorHandler.handle(this, e)) {
                        Toast.makeText(this, "Failed to upload avatar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { ApiErrorHandler.handleGeneric(this, e) }
            }
        }.start()
    }

    private fun showEditUsernameDialog() {
        val current = tvUsername.text.toString()
        val input = EditText(this).apply {
            setText(current)
            setSelection(current.length)
            setPadding(48, 48, 48, 48)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_username)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.length < 3) {
                    Toast.makeText(this, "Username must be at least 3 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == current) return@setPositiveButton
                changeUsername(newName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun changeUsername(newUsername: String) {
        val token = TokenManager.getToken(this)
        Thread {
            try {
                val resp = ProfileApi.changeUsername(token, newUsername)
                val username = resp.optString("username", newUsername)
                runOnUiThread {
                    tvUsername.text = username
                    TokenManager.saveUsername(this, username)
                    Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    if (!ApiErrorHandler.handle(this, e)) {
                        if (e.code == 409) {
                            Toast.makeText(this, R.string.username_taken, Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                val errBody = JSONObject(e.message ?: "")
                                Toast.makeText(this, errBody.optString("detail", getString(R.string.network_error)), Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { ApiErrorHandler.handleGeneric(this, e) }
            }
        }.start()
    }

    private fun showEditEmailDialog() {
        val current = tvEmail.text.toString()
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            if (current != getString(R.string.not_set)) setText(current)
            setSelection(current.length)
            setPadding(48, 48, 48, 48)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.edit_email)
            .setView(input)
            .setPositiveButton(R.string.save, null) // listener set after show
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        // Intercept save button to prevent dialog dismiss on validation failure
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val email = input.text.toString().trim()
            if (email.isEmpty()) {
                input.error = getString(R.string.email_required)
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                input.error = getString(R.string.email_invalid)
                return@setOnClickListener
            }
            dialog.dismiss()
            bindEmail(email)
        }
    }

    private fun bindEmail(email: String) {
        val token = TokenManager.getToken(this)
        Thread {
            try {
                val resp = ProfileApi.bindEmail(token, email)
                if (resp.optBoolean("conflict", false)) {
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.email_conflict_title))
                            .setMessage(getString(R.string.email_conflict))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                } else {
                    runOnUiThread {
                        tvEmail.text = email
                        btnEditEmail.text = getString(R.string.change_email)
                        Toast.makeText(this, R.string.email_bound, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    if (!ApiErrorHandler.handle(this, e)) {
                        if (e.code == 409) {
                            AlertDialog.Builder(this)
                                .setTitle(getString(R.string.email_conflict_title))
                                .setMessage(getString(R.string.email_conflict))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        } else {
                            Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { ApiErrorHandler.handleGeneric(this, e) }
            }
        }.start()
    }

    private fun toggleMetricsEdit() {
        isMetricsEditing = !isMetricsEditing
        val showEdit = isMetricsEditing
        // Toggle display vs edit fields
        tvHeight.visibility = if (showEdit) View.GONE else View.VISIBLE
        tvWeight.visibility = if (showEdit) View.GONE else View.VISIBLE
        tvAge.visibility = if (showEdit) View.GONE else View.VISIBLE
        tilHeight.visibility = if (showEdit) View.VISIBLE else View.GONE
        tilWeight.visibility = if (showEdit) View.VISIBLE else View.GONE
        tilAge.visibility = if (showEdit) View.VISIBLE else View.GONE
        btnSaveMetrics.visibility = if (showEdit) View.VISIBLE else View.GONE
    }

    private fun hasMetricChanges(): Boolean {
        val h = etHeight.text.toString().trim().toIntOrNull()
        val w = etWeight.text.toString().trim().toDoubleOrNull()
        val a = etAge.text.toString().trim().toIntOrNull()
        return h != origHeight || w != origWeight || a != origAge
    }

    private fun saveMetrics() {
        if (!hasMetricChanges()) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }
        val heightStr = etHeight.text.toString().trim()
        val weightStr = etWeight.text.toString().trim()
        val ageStr = etAge.text.toString().trim()

        val json = JSONObject()
        if (heightStr.isNotEmpty()) json.put("heightCm", heightStr.toIntOrNull())
        if (weightStr.isNotEmpty()) json.put("weightKg", weightStr.toDoubleOrNull())
        if (ageStr.isNotEmpty()) json.put("age", ageStr.toIntOrNull())

        if (json.length() == 0) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }

        btnSaveMetrics.isEnabled = false
        val token = TokenManager.getToken(this)
        Thread {
            try {
                ProfileApi.updateProfile(token, json)
                runOnUiThread {
                    btnSaveMetrics.isEnabled = true
                    // Update originals and display values, exit edit mode
                    val h = heightStr.toIntOrNull(); val w = weightStr.toDoubleOrNull(); val a = ageStr.toIntOrNull()
                    origHeight = h; origWeight = w; origAge = a
                    tvHeight.text = h?.let { "$it cm" } ?: getString(R.string.not_set)
                    tvWeight.text = w?.let { "$it kg" } ?: getString(R.string.not_set)
                    tvAge.text = a?.toString() ?: getString(R.string.not_set)
                    etHeight.setText(h?.toString() ?: "")
                    etWeight.setText(w?.toString() ?: "")
                    etAge.setText(a?.toString() ?: "")
                    // Switch back to display mode
                    isMetricsEditing = false
                    tvHeight.visibility = View.VISIBLE
                    tvWeight.visibility = View.VISIBLE
                    tvAge.visibility = View.VISIBLE
                    tilHeight.visibility = View.GONE
                    tilWeight.visibility = View.GONE
                    tilAge.visibility = View.GONE
                    btnSaveMetrics.visibility = View.GONE
                    Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    btnSaveMetrics.isEnabled = true
                    if (!ApiErrorHandler.handle(this, e)) {
                        Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnSaveMetrics.isEnabled = true
                    ApiErrorHandler.handleGeneric(this, e)
                }
            }
        }.start()
    }
}
