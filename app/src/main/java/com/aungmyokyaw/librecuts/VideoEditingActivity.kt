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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aungmyokyaw.librecuts.databinding.ActivityVideoEditingBinding
import com.aungmyokyaw.librecuts.models.EditOperation
import com.aungmyokyaw.librecuts.models.TextPosition
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
    private var exportDialog: android.app.AlertDialog? = null
    private var outputFile: File? = null

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

        binding.btnAudio.setOnClickListener {
            showAudioDialog()
        }

        binding.btnFilters.setOnClickListener {
            showFiltersDialog()
        }

        binding.btnSrt.setOnClickListener {
            showSrtFilePicker()
        }

        binding.btnAudioFile.setOnClickListener {
            showAudioFilePicker()
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_trim, null)
        val seekBarStart = dialogView.findViewById<android.widget.SeekBar>(R.id.seekBarStart)!!
        val seekBarEnd = dialogView.findViewById<android.widget.SeekBar>(R.id.seekBarEnd)!!
        val tvStartTime = dialogView.findViewById<android.widget.TextView>(R.id.tvStartTime)!!
        val tvEndTime = dialogView.findViewById<android.widget.TextView>(R.id.tvEndTime)!!
        val tvTotalTime = dialogView.findViewById<android.widget.TextView>(R.id.tvTotalTime)!!

        seekBarStart.max = videoDurationMs.toInt()
        seekBarEnd.max = videoDurationMs.toInt()
        seekBarEnd.progress = videoDurationMs.toInt()
        tvTotalTime.text = formatTime(videoDurationMs)
        tvStartTime.text = formatTime(0)
        tvEndTime.text = formatTime(videoDurationMs)

        seekBarStart.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress >= seekBarEnd.progress) seekBar.progress = seekBarEnd.progress - 1000
                tvStartTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        seekBarEnd.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress <= seekBarStart.progress) seekBar.progress = seekBarStart.progress + 1000
                tvEndTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        android.app.AlertDialog.Builder(this)
            .setTitle("Trim Video")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val startMs = seekBarStart.progress.toLong()
                val endMs = seekBarEnd.progress.toLong()
                viewModel.addOperation(EditOperation.Trim(startMs, endMs))
                Toast.makeText(this, "Trim applied: ${formatTime(startMs)} - ${formatTime(endMs)}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "4.0x")
        val speedValues = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 4.0f)

        android.app.AlertDialog.Builder(this)
            .setTitle("Video Speed")
            .setItems(speeds) { _, which ->
                viewModel.addOperation(EditOperation.SpeedMain(speedValues[which]))
                Toast.makeText(this, "Speed set to ${speeds[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text, null)
        val etText = dialogView.findViewById<android.widget.EditText>(R.id.etText)!!
        val etFontSize = dialogView.findViewById<android.widget.EditText>(R.id.etFontSize)!!
        val spinnerPosition = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerPosition)!!

        etFontSize.setText("48")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, TextPosition.labels())
        spinnerPosition.adapter = adapter

        android.app.AlertDialog.Builder(this)
            .setTitle("Add Text Overlay")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val text = etText.text.toString()
                val fontSize = etFontSize.text.toString().toIntOrNull() ?: 48
                val position = TextPosition.fromLabel(spinnerPosition.selectedItem.toString())
                if (text.isNotEmpty()) {
                    viewModel.addOperation(EditOperation.AddText(text, fontSize, position))
                    Toast.makeText(this, "Text added: $text", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMergeDialog() {
        Toast.makeText(this, "Select videos to merge", Toast.LENGTH_SHORT).show()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_MERGE_VIDEOS)
    }

    private fun showAudioDialog() {
        val options = arrayOf("Mute Audio", "Extract Audio", "Replace Audio")
        android.app.AlertDialog.Builder(this)
            .setTitle("Audio Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.addOperation(EditOperation.MuteAudio())
                        Toast.makeText(this, "Audio muted", Toast.LENGTH_SHORT).show()
                    }
                    1 -> Toast.makeText(this, "Extract audio feature", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Replace audio feature", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFiltersDialog() {
        val filters = arrayOf("None", "Sepia", "Grayscale", "Blur", "Brightness+", "Contrast+", "Vintage", "Warm")
        android.app.AlertDialog.Builder(this)
            .setTitle("Video Filters")
            .setItems(filters) { _, which ->
                val filterName = if (which == 0) "null" else filters[which].lowercase()
                viewModel.addOperation(EditOperation.ColorFilter(0, filterName))
                Toast.makeText(this, "Filter applied: ${filters[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSrtFilePicker() {
        Toast.makeText(this, "Select SRT subtitle file", Toast.LENGTH_SHORT).show()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/x-subrip"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_SRT_FILE)
    }

    private fun showAudioFilePicker() {
        Toast.makeText(this, "Select MP3 audio file", Toast.LENGTH_SHORT).show()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_AUDIO_FILE)
    }

    private fun startExport() {
        if (!isVideoLoaded) {
            Toast.makeText(this, "Video not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.startExport()
        showExportDialog()

        exportJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: File(filesDir, "exports")
                outputDir.mkdirs()

                outputFile = File(outputDir, "export_${System.currentTimeMillis()}.mp4")

                updateExportProgress(0, "Processing video...")

                val result = ffmpegEngine.trimVideo(
                    sourceFilePath = tempInputFile.absolutePath,
                    startMs = 0,
                    endMs = videoDurationMs,
                    outputFilePath = outputFile!!.absolutePath
                )

                withContext(Dispatchers.Main) {
                    when (result) {
                        is FFmpegRenderEngine.RenderResult.Success -> {
                            viewModel.finishExport()
                            showExportComplete()
                        }
                        is FFmpegRenderEngine.RenderResult.Failure -> {
                            viewModel.exportError(result.error)
                            showExportError(result.error)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    viewModel.exportError(e.message ?: "Export failed")
                    showExportError(e.message ?: "Export failed")
                }
            }
        }
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export, null)
        exportDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Exporting Video")
            .setView(dialogView)
            .setCancelable(false)
            .create()
        exportDialog?.show()
    }

    private fun updateExportProgress(progress: Int, status: String) {
        runOnUiThread {
            exportDialog?.let { dialog ->
                val dialogView = dialog.window?.decorView
                val progressBar = dialogView?.findViewById<ProgressBar>(R.id.progressBar)
                val tvProgress = dialogView?.findViewById<android.widget.TextView>(R.id.tvProgress)
                val tvStatus = dialogView?.findViewById<android.widget.TextView>(R.id.tvStatus)
                progressBar?.progress = progress
                tvProgress?.text = "$progress%"
                tvStatus?.text = status
            }
        }
    }

    private fun showExportComplete() {
        exportDialog?.let { dialog ->
            val dialogView = dialog.window?.decorView
            val progressBar = dialogView?.findViewById<ProgressBar>(R.id.progressBar)
            val tvProgress = dialogView?.findViewById<android.widget.TextView>(R.id.tvProgress)
            val tvStatus = dialogView?.findViewById<android.widget.TextView>(R.id.tvStatus)
            val layoutComplete = dialogView?.findViewById<LinearLayout>(R.id.layoutComplete)
            val btnShare = dialogView?.findViewById<Button>(R.id.btnShare)

            dialog.setTitle("Export Complete!")
            progressBar?.progress = 100
            tvProgress?.text = "100%"
            tvStatus?.text = "Video saved successfully"
            layoutComplete?.visibility = View.VISIBLE

            btnShare?.setOnClickListener {
                outputFile?.let { file ->
                    shareVideo(file)
                }
            }
        }
    }

    private fun showExportError(error: String) {
        exportDialog?.dismiss()
        Toast.makeText(this, "Export failed: $error", Toast.LENGTH_LONG).show()
    }

    private fun shareVideo(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Video"))
        exportDialog?.dismiss()
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_SRT_FILE -> {
                data.data?.let { uri ->
                    try {
                        val srtContent = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                        val fileName = getFileName(uri)
                        viewModel.addOperation(EditOperation.AddSubtitles(uri, srtContent, fileName, emptyList()))
                        Toast.makeText(this, "Subtitles added: $fileName", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to load subtitles", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            REQUEST_AUDIO_FILE -> {
                data.data?.let { uri ->
                    val fileName = getFileName(uri)
                    viewModel.addOperation(EditOperation.AddBackgroundAudio(uri, false, 1.0f))
                    Toast.makeText(this, "Audio added: $fileName", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_MERGE_VIDEOS -> {
                // Handle merge videos
                Toast.makeText(this, "Videos selected for merge", Toast.LENGTH_SHORT).show()
            }
        }
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
        private const val REQUEST_MERGE_VIDEOS = 1001
        private const val REQUEST_SRT_FILE = 1002
        private const val REQUEST_AUDIO_FILE = 1003
    }
}
