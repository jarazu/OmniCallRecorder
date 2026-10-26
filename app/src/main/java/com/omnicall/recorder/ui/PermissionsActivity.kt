package com.omnicall.recorder.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.omnicall.recorder.R
import com.omnicall.recorder.databinding.ActivityPermissionsBinding
import com.omnicall.recorder.services.CallDetectionAccessibilityService
import com.omnicall.recorder.services.CallNotificationListener

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatus() }

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupClickListeners() {
        binding.btnGrantMic.setOnClickListener {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding.btnGrantPhone.setOnClickListener {
            val permissions = mutableListOf(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                permissions.add(Manifest.permission.READ_CALL_LOG)
            }
            phonePermissionLauncher.launch(permissions.toTypedArray())
        }

        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            }
        }

        binding.btnFinishSetup.setOnClickListener {
            finish()
        }
    }

    private fun updateStatus() {
        // 1. Microphone
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        updateItemUI(binding.ivCheckMic, binding.btnGrantMic, hasMic)

        // 2. Phone State
        val hasPhone = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        updateItemUI(binding.ivCheckPhone, binding.btnGrantPhone, hasPhone)

        // 3. Accessibility Service
        val hasAccessibility = isAccessibilityServiceEnabled(this, CallDetectionAccessibilityService::class.java)
        updateItemUI(binding.ivCheckAccessibility, binding.btnGrantAccessibility, hasAccessibility)

        // 4. Notification Listener
        val hasNotification = isNotificationListenerEnabled(this)
        updateItemUI(binding.ivCheckNotification, binding.btnGrantNotification, hasNotification)

        // 5. Battery Optimization
        val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
        updateItemUI(binding.ivCheckBattery, binding.btnGrantBattery, isBatteryIgnored)
    }

    private fun updateItemUI(
        icon: android.widget.ImageView,
        button: com.google.android.material.button.MaterialButton,
        isGranted: Boolean
    ) {
        if (isGranted) {
            icon.setImageResource(R.drawable.ic_check_circle)
            button.text = "Granted"
            button.isEnabled = false
            button.alpha = 0.5f
        } else {
            icon.setImageResource(R.drawable.ic_alert_circle)
            button.text = "Grant"
            button.isEnabled = true
            button.alpha = 1.0f
        }
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
            val expectedComponentName = ComponentName(context, serviceClass).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.contains(pkgName)
        }

        fun areAllEssentialPermissionsGranted(context: Context): Boolean {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val hasPhone = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val hasAccessibility = isAccessibilityServiceEnabled(
                context,
                CallDetectionAccessibilityService::class.java
            )

            return hasMic && hasPhone && hasAccessibility
        }
    }
}
