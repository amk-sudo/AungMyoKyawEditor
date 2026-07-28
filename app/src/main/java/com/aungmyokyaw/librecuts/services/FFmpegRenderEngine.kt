package com.aungmyokyaw.librecuts.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.antonkarpenko.ffmpegkit.Level
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FFmpegRenderEngine handles all FFmpeg operations for the video editor.
 */
class FFmpegRenderEngine(private val context: Context) {

    private val activeSessions = mutableListOf<FFmpegSession>()
    private val TAG = "FFmpegRenderEngine"

    init {
        // Set font directory for drawtext filter
        val fontDir = listOf(
            "/system/fonts",
            "/system/font",
            "/data/fonts",
            "/product/fonts"
        ).firstOrNull { File(it).isDirectory } ?: "/system/fonts"

        try {
            FFmpegKitConfig.setFontDirectory(context, fontDir, null)
            Log.d(TAG, "Font directory set to: $fontDir")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set font directory '$fontDir': ${e.message}")
        }

        // Register global log callback
        try {
            FFmpegKitConfig.enableLogCallback { log ->
                val msg = log.message?.trimEnd() ?: return@enableLogCallback
                if (msg.isEmpty()) return@enableLogCallback
                when (log.level) {
                    Level.AV_LOG_ERROR, Level.AV_LOG_FATAL, Level.AV_LOG_PANIC, Level.AV_LOG_STDERR
                        -> Log.e(TAG, "[ffmpeg] $msg")
                    Level.AV_LOG_WARNING -> Log.w(TAG, "[ffmpeg] $msg")
                    else -> Log.v(TAG, "[ffmpeg] $msg")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register FFmpegKit global log callback: ${e.message}")
        }
    }

    // ── Result type ───────────────────────────────────────────────────────────

    sealed class RenderResult {
        data class Success(val outputPath: String, val session: FFmpegSession) : RenderResult()
        data class Failure(val error: String, val session: FFmpegSession? = null) : RenderResult()
        object Cancelled : RenderResult()
    }

    // ── Font helper ───────────────────────────────────────────────────────────

    /**
     * Copies a font from assets to cacheDir so FFmpeg can access it.
     */
    fun copyFontToCache(assetPath: String = "fonts/Roboto-Regular.ttf"): String? {
        return try {
            val fileName = assetPath.substringAfterLast('/')
            val fontFile = File(context.cacheDir, fileName)
            if (!fontFile.exists()) {
                context.assets.open(assetPath).use { input ->
                    fontFile.outputStream().use { input.copyTo(it) }
                }
                Log.d(TAG, "Font copied to cache: ${fontFile.absolutePath}")
            }
            
            val fontMap = mutableMapOf<String, String>()
            val alias = fileName.substringBeforeLast('.')
            fontMap[alias] = fileName
            FFmpegKitConfig.setFontDirectory(context, context.cacheDir.absolutePath, fontMap)
            
            alias
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy font to cache: ${e.message}", e)
            null
        }
    }

    // ── Core execution ────────────────────────────────────────────────────────

    /**
     * Execute an FFmpeg command and return the result.
     */
    suspend fun executeCommand(command: String): RenderResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Executing FFmpeg command: $command")
                val session = FFmpegKit.execute(command)
                activeSessions.add(session)

                val returnCode = session.returnCode
                Log.d(TAG, "FFmpeg completed with return code: $returnCode")

                if (ReturnCode.isSuccess(returnCode)) {
                    RenderResult.Success(
                        outputPath = extractOutputPath(command),
                        session = session
                    )
                } else {
                    val failLog = session.failStackTrace?.takeIf { it.isNotBlank() }
                        ?: session.allLogsAsString?.takeIf { it.isNotBlank() }
                        ?: "FFmpeg exited with code ${returnCode?.value}"

                    if (command.contains("h264_mediacodec")) {
                        Log.w(TAG, "Hardware encoder failed. Falling back to libx264.")
                        val fallbackCommand = command.replace("h264_mediacodec", "libx264")
                        return@withContext executeCommand(fallbackCommand)
                    }

                    Log.e(TAG, "FFmpeg error: $failLog")
                    RenderResult.Failure(error = failLog, session = session)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during FFmpeg execution: ${e.message}", e)
                RenderResult.Failure(error = e.message ?: "Unknown exception")
            }
        }
    }

    // ── Session management ────────────────────────────────────────────────────

    suspend fun cancelAllSessions() {
        withContext(Dispatchers.IO) {
            FFmpegKit.cancel()
            activeSessions.clear()
            Log.d(TAG, "Cancelled all FFmpeg sessions")
        }
    }

    suspend fun cancelSession(sessionId: Long) {
        withContext(Dispatchers.IO) {
            FFmpegKit.cancel(sessionId)
            activeSessions.removeIf { it.sessionId == sessionId }
            Log.d(TAG, "Cancelled session: $sessionId")
        }
    }

    fun hasActiveSessions(): Boolean = activeSessions.isNotEmpty()
    fun getActiveSessionCount(): Int = activeSessions.size

    // ── Video operations ──────────────────────────────────────────────────────

    suspend fun trimVideo(
        sourceFilePath: String,
        startMs: Long,
        endMs: Long,
        outputFilePath: String
    ): RenderResult {
        val startSecs = startMs / 1000.0
        val durationSecs = (endMs - startMs) / 1000.0
        val command = "-ss $startSecs -i \"$sourceFilePath\" -to $durationSecs -c copy \"$outputFilePath\""
        return executeCommand(command)
    }

    suspend fun generateSpeedProxy(
        sourceFilePath: String,
        startMs: Long,
        endMs: Long,
        speed: Float,
        outputFilePath: String
    ): RenderResult {
        val startSecs = startMs / 1000.0
        val durationSecs = (endMs - startMs) / 1000.0
        val ptsMultiplier = 1.0f / speed
        
        val atempoFilters = mutableListOf<String>()
        var currentSpeed = speed
        while (currentSpeed < 0.5f) {
            atempoFilters.add("atempo=0.5")
            currentSpeed /= 0.5f
        }
        while (currentSpeed > 2.0f) {
            atempoFilters.add("atempo=2.0")
            currentSpeed /= 2.0f
        }
        if (currentSpeed != 1.0f || atempoFilters.isEmpty()) {
            atempoFilters.add("atempo=$currentSpeed")
        }
        val audioFilter = atempoFilters.joinToString(",")
        
        val command = "-y -ss $startSecs -t $durationSecs -i \"$sourceFilePath\" -filter:v \"setpts=${ptsMultiplier}*PTS,format=yuv420p\" -filter:a \"$audioFilter\" \"$outputFilePath\""
        return executeCommand(command)
    }

    suspend fun reverseVideo(
        sourceFilePath: String,
        startMs: Long,
        endMs: Long,
        outputFilePath: String
    ): RenderResult {
        val startSecs = startMs / 1000.0
        val durationSecs = (endMs - startMs) / 1000.0
        val command = "-y -ss $startSecs -i \"$sourceFilePath\" -to $durationSecs -filter:v \"reverse,format=yuv420p\" -filter:a \"areverse\" -c:v libx264 -c:a aac \"$outputFilePath\""
        return executeCommand(command)
    }

    suspend fun cropVideo(
        sourceFilePath: String,
        aspectRatio: String,
        outputFilePath: String
    ): RenderResult {
        val cropFilter = buildCropFilter(aspectRatio)
            ?: return RenderResult.Failure("Invalid aspect ratio: $aspectRatio")
        val command = "-i \"$sourceFilePath\" -vf \"$cropFilter\" -c:a copy \"$outputFilePath\""
        return executeCommand(command)
    }

    suspend fun addTextOverlay(
        sourceFilePath: String,
        text: String,
        fontSize: Int,
        positionParam: String,
        outputFilePath: String,
        fontFilePath: String? = null
    ): RenderResult {
        val textFilter = buildDrawtextFilter(text, fontSize, positionParam, fontFilePath)
        val command = "-i \"$sourceFilePath\" -vf \"$textFilter\" -c:a copy \"$outputFilePath\""
        return executeCommand(command)
    }

    suspend fun addBackgroundAudio(
        sourceFilePath: String,
        audioFilePath: String,
        outputFilePath: String,
        replaceAudio: Boolean = false
    ): RenderResult {
        val command = if (replaceAudio) {
            "-i \"$sourceFilePath\" -i \"$audioFilePath\" -c:v copy -map 0:v:0 -map 1:a:0 \"$outputFilePath\""
        } else {
            "-i \"$sourceFilePath\" -i \"$audioFilePath\" " +
                    "-filter_complex \"[0:a][1:a]amix=inputs=2:duration=first[a]\" " +
                    "-map 0:v -map \"[a]\" -c:v copy \"$outputFilePath\""
        }
        return executeCommand(command)
    }

    suspend fun muteAudio(sourceFilePath: String, outputFilePath: String): RenderResult {
        val command = "-i \"$sourceFilePath\" -an -c:v copy \"$outputFilePath\""
        return executeCommand(command)
    }

    suspend fun extractFrame(
        sourceFilePath: String,
        timeMs: Long,
        outputImagePath: String
    ): RenderResult {
        val timeSecs = timeMs / 1000.0
        val command = "-ss $timeSecs -i \"$sourceFilePath\" -vframes 1 \"$outputImagePath\""
        return executeCommand(command)
    }

    suspend fun generateVideoFromImage(
        imageUri: Uri,
        durationMs: Long,
        outputPath: String
    ): RenderResult {
        val imagePath = FFmpegKitConfig.getSafParameterForRead(context, imageUri)
        val durationSecs = durationMs / 1000f
        val command = "-f image2 -loop 1 -framerate 30 -i \"$imagePath\" -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=44100 -vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" -c:v libx264 -t $durationSecs -pix_fmt yuv420p -c:a aac -shortest \"$outputPath\""
        return executeCommand(command)
    }

    suspend fun generateVideoFromGif(
        gifUri: Uri,
        durationMs: Long,
        outputPath: String
    ): RenderResult {
        val gifPath = FFmpegKitConfig.getSafParameterForRead(context, gifUri)
        val durationSecs = durationMs / 1000f
        val command = "-f gif -ignore_loop 0 -i \"$gifPath\" -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=44100 -vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" -c:v libx264 -t $durationSecs -pix_fmt yuv420p -c:a aac -shortest \"$outputPath\""
        return executeCommand(command)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun cleanup() {
        activeSessions.clear()
        Log.d(TAG, "FFmpegRenderEngine cleaned up")
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildDrawtextFilter(
        text: String,
        fontSize: Int,
        positionParam: String,
        fontFilePath: String?
    ): String {
        val escapedText = text
            .replace("\\", "\\\\")
            .replace("'", "\\\\'")
            .replace(":", "\\:")

        val fontPart = if (!fontFilePath.isNullOrBlank()) {
            val escapedFontPath = fontFilePath
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace(":", "\\:")
            "fontfile='$escapedFontPath':"
        } else {
            Log.w(TAG, "No fontFilePath — text may not render.")
            ""
        }

        return "drawtext=${fontPart}text='$escapedText':fontcolor=white:fontsize=$fontSize:$positionParam"
    }

    private fun buildCropFilter(aspectRatio: String): String? = when (aspectRatio) {
        "16:9" -> "crop='trunc(min(iw,ih*16/9)/2)*2':'trunc(min(ih,iw*9/16)/2)*2',setsar=1"
        "9:16" -> "crop='trunc(min(iw,ih*9/16)/2)*2':'trunc(min(ih,iw*16/9)/2)*2',setsar=1"
        "1:1"  -> "crop='trunc(min(iw,ih)/2)*2':'trunc(min(iw,ih)/2)*2',setsar=1"
        else   -> null
    }

    private fun extractOutputPath(command: String): String {
        val quotedRegex = """"([^"]*)"\s*$""".toRegex()
        quotedRegex.find(command)?.groupValues?.get(1)?.let { return it }
        return command.trimEnd().split("\\s+".toRegex()).lastOrNull() ?: ""
    }
}
