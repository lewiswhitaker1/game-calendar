package me.lewis.gcalendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Notifications enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "You won't get game alerts without notifications.", Toast.LENGTH_LONG).show()
            }
        }

        askNotificationPermission()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getString("LOGGED_IN_USER", null) != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val spinnerName: AutoCompleteTextView = findViewById(R.id.spinnerName)
        val editTextPin: EditText = findViewById(R.id.editTextPin)
        val buttonLogin: Button = findViewById(R.id.buttonLogin)

        val users = arrayOf("Lewis", "Joe", "Polly")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, users)
        spinnerName.setAdapter(adapter)

        buttonLogin.setOnClickListener {
            val name = spinnerName.text.toString()
            val pin = editTextPin.text.toString()

            if (pin.isEmpty()) {
                Toast.makeText(this, "Please enter a PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(this, "Failed to get notification token", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                val fcmToken = task.result
                performLogin(name, pin, fcmToken)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun performLogin(name: String, pin: String, fcmToken: String) {
        val request = LoginRequest(name, pin, fcmToken)
        RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(this@LoginActivity)
                    prefs.edit().putString("LOGGED_IN_USER", name).apply()

                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Invalid name or PIN", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(this@LoginActivity, "Error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}