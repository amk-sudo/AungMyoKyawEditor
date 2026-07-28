package com.aungmyokyaw.librecuts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aungmyokyaw.librecuts.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val selectVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("VideoSelection", "Could not take persistable permission", e)
                }
                navigateToEditingScreen(uri)
            }
        }

    private val openProjectLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("ProjectSelection", "Could not take persistable permission", e)
                }
                val intent = Intent(this, ProjectImportActivity::class.java).apply {
                    putExtra("PROJECT_URI", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                startActivity(intent)
            }
        }

    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    val prefs = getSharedPreferences("aungmyokyaw_prefs", MODE_PRIVATE)
                    prefs.edit().putString("export_directory_uri", uri.toString()).apply()
                } catch (e: Exception) {
                    Log.e("FolderSelection", "Error securing permission", e)
                }
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                selectVideoLauncher.launch(arrayOf("video/*"))
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupUI() {
        binding.btnImport.setOnClickListener {
            checkPermissionsAndSelectVideo()
        }

        binding.btnOpenProject.setOnClickListener {
            openProjectLauncher.launch(arrayOf("*/*"))
        }

        binding.btnChangeExportFolder.setOnClickListener {
            selectFolderLauncher.launch(null)
        }
    }

    private fun checkPermissionsAndSelectVideo() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            selectVideoLauncher.launch(arrayOf("video/*"))
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun navigateToEditingScreen(videoUri: Uri) {
        Log.d("Navigation", "Navigating to editing screen")
        val intent = Intent(this, VideoEditingActivity::class.java).apply {
            putExtra("VIDEO_URI", videoUri)
            data = videoUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("video/")) {
                (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                    Log.d("SharedVideo", "Received video: $uri")
                    navigateToEditingScreen(uri)
                }
            }
        }
    }
}
