package com.aungmyokyaw.librecuts

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aungmyokyaw.librecuts.databinding.ActivityVideoEditingBinding
import com.aungmyokyaw.librecuts.models.VideoProject
import com.aungmyokyaw.librecuts.services.FFmpegRenderEngine
import com.aungmyokyaw.librecuts.viewmodels.VideoEditingViewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("DEPRECATION")
class VideoEditingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditingBinding
    private lateinit var viewModel: VideoEditingViewModel
    private lateinit var ffmpegEngine: FFmpegRenderEngine
    private lateinit var player: ExoPlayer
    private lateinit var playerView: StyledPlayerView

    private var videoUri: Uri? = null
    private var videoFileName: String = ""
    private lateinit var tempInputFile: File
    private var exportJob: Job? = null
    private var frameExtractionJob: Job? = null
    private var isVideoLoaded = false
    private var videoDurationMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[VideoEditingViewModel::class.java]
        ffmpegEngine = FFmpegRenderEngine(this)

        setupPlayer()
        setupUI()
        handleIntent()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView = binding.playerView
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        binding.progressLoading.visibility = View.GONE
                        isVideoLoaded = true
                    }
                    Player.STATE_BUFFERING -> {
                        binding.progressLoading.visibility = View.VISIBLE
                    }
                    Player.STATE_ENDED -> {
                        player.seekTo(0)
                        player.pause()
                        updatePlayPauseButton(false)
                    }
                }
            }
        })
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.btnUndo.setOnClickListener {
            viewModel.undo()
            refreshPreview()
        }

        binding.btnRedo.setOnClickListener {
            viewModel.redo()
            refreshPreview()
        }

        binding.btnExport.setOnClickListener {
            startExport()
        }

        binding.btnTrim.setOnClickListener {
            showTrimDialog()
        }

        binding.btnSpeed.setOnClickListener {
            showSpeedDialog()
        }

        binding.btnText.setOnClickListener {
            showTextDialog()
        }

        binding.btnMerge.setOnClickListener {
            showMergeDialog()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.hasUnsavedEdits.value) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun handleIntent() {
        videoUri = intent.data ?: intent.getParcelableExtra("VIDEO_URI")
        if (videoUri == null) {
            Toast.makeText(this, "No video selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        videoFileName = getFileName(videoUri!!)
        binding.tvVideoTitle.text = videoFileName

        lifecycleScope.launch {
            loadVideo()
        }
    }

    private suspend fun loadVideo() {
        withContext(Dispatchers.IO) {
            try {
                tempInputFile = File.createTempFile("input_video", ".mp4", cacheDir)
                contentResolver.openInputStream(videoUri!!)?.use { input ->
                    tempInputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(tempInputFile.absolutePath)
                videoDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()

                withContext(Dispatchers.Main) {
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(tempInputFile))
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    
                    viewModel.initializeProject(videoUri!!, videoFileName)
                    updateDurationText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoEditingActivity, "Failed to load video", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            updatePlayPauseButton(false)
        } else {
            player.play()
            updatePlayPauseButton(true)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun updateDurationText() {
        val currentMs = player.currentPosition
        val totalMs = videoDurationMs
        binding.tvCurrentTime.text = formatTime(currentMs)
        binding.tvTotalTime.text = formatTime(totalMs)
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun refreshPreview() {
        // Re-apply current state
        Log.d(TAG, "Refreshing preview")
    }

    private fun showTrimDialog() {
        // Simplified trim - would open a trim UI
        Toast.makeText(this, "Trim feature", Toast.LENGTH_SHORT).show()
    }

    private fun showSpeedDialog() {
        Toast.makeText(this, "Speed feature", Toast.LENGTH_SHORT).show()
    }

    private fun showTextDialog() {
        Toast.makeText(this, "Add text feature", Toast.LENGTH_SHORT).show()
    }

    private fun showMergeDialog() {
        Toast.makeText(this, "Merge videos feature", Toast.LENGTH_SHORT).show()
    }

    private fun startExport() {
        if (!isVideoLoaded) {
            Toast.makeText(this, "Video not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.startExport()
        binding.exportProgress.visibility = View.VISIBLE

        exportJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: File(filesDir, "exports")
                outputDir.mkdirs()

                val outputFile = File(outputDir, "export_${System.currentTimeMillis()}.mp4")

                val result = ffmpegEngine.trimVideo(
                    sourceFilePath = tempInputFile.absolutePath,
                    startMs = 0,
                    endMs = videoDurationMs,
                    outputFilePath = outputFile.absolutePath
                )

                withContext(Dispatchers.Main) {
                    when (result) {
                        is FFmpegRenderEngine.RenderResult.Success -> {
                            viewModel.finishExport()
                            Toast.makeText(this@VideoEditingActivity, "Export complete!", Toast.LENGTH_SHORT).show()
                            binding.exportProgress.visibility = View.GONE
                        }
                        is FFmpegRenderEngine.RenderResult.Failure -> {
                            viewModel.exportError(result.error)
                            Toast.makeText(this@VideoEditingActivity, "Export failed", Toast.LENGTH_SHORT).show()
                            binding.exportProgress.visibility = View.GONE
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    viewModel.exportError(e.message ?: "Export failed")
                    binding.exportProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun showUnsavedChangesDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to save before exiting?")
            .setPositiveButton("Save") { _, _ ->
                finish()
            }
            .setNegativeButton("Discard") { _, _ ->
                finish()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun getFileName(uri: Uri): String {
        var name = "video.mp4"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        exportJob?.cancel()
        frameExtractionJob?.cancel()
        ffmpegEngine.cleanup()
        if (::tempInputFile.isInitialized) {
            tempInputFile.delete()
        }
    }

    companion object {
        private const val TAG = "VideoEditingActivity"
    }
}
