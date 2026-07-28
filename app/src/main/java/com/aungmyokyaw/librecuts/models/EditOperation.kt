package com.aungmyokyaw.librecuts.models

import android.net.Uri
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Sealed class representing all possible edit operations.
 */
sealed class EditOperation : Serializable {

    /**
     * Trim operation: Cuts video from startMs to endMs.
     */
    data class Trim(
        val startMs: Long,
        val endMs: Long,
        val id: String = System.nanoTime().toString()
    ) : EditOperation() {
        init {
            require(startMs >= 0) { "Start time cannot be negative" }
            require(endMs > startMs) { "End time must be greater than start time" }
        }
    }

    /**
     * Speed operation for main video.
     */
    data class SpeedMain(
        val speed: Float,
        val proxyUri: Uri? = null,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Reverse operation for main video.
     */
    data class ReverseMain(
        val isReversed: Boolean,
        val proxyUri: Uri? = null,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Mirror operation for main video.
     */
    data class MirrorMain(
        val isMirrored: Boolean,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Crop operation: Crops video to aspect ratio.
     */
    data class Crop(
        val aspectRatio: String,
        val xFraction: Float = 0f,
        val yFraction: Float = 0f,
        val wFraction: Float = 1f,
        val hFraction: Float = 1f,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Keyframe point for animations.
     */
    data class KeyframePoint(
        val timeMs: Long,
        val valueX: Float,
        val valueY: Float = 0f,
        val interpolationType: String = "linear"
    ) : Serializable

    /**
     * Add text overlay.
     */
    data class AddText(
        val text: String,
        val fontSize: Int,
        val position: TextPosition,
        val relativeX: Float? = null,
        val relativeY: Float? = null,
        val color: String = "#FFFFFF",
        val startTimeMs: Long? = null,
        val endTimeMs: Long? = null,
        val id: String = System.nanoTime().toString(),
        val fontPath: String? = null,
        val opacity: Float = 1.0f,
        val borderThickness: Int = 0,
        val borderColor: String = "#000000",
        val textAlign: String = "center",
        val letterSpacing: Float = 0f,
        val lineSpacing: Float = 0f,
        val positionKeyframes: List<KeyframePoint> = emptyList(),
        val opacityKeyframes: List<KeyframePoint> = emptyList()
    ) : EditOperation() {
        init {
            require(text.isNotEmpty()) { "Text cannot be empty" }
            require(fontSize > 0) { "Font size must be positive" }
            relativeX?.let { require(it in 0f..1f) }
            relativeY?.let { require(it in 0f..1f) }
            require(opacity in 0f..1f)
        }

        fun hasCustomPosition(): Boolean = relativeX != null && relativeY != null
    }

    enum class MaskShape { NONE, SPLIT, SHUTTER, ELLIPSE, RECTANGLE, HEART, STAR }

    data class MaskConfig(
        val shape: MaskShape = MaskShape.NONE,
        val relativeX: Float = 0.5f,
        val relativeY: Float = 0.5f,
        val relativeWidth: Float = 0.5f,
        val relativeHeight: Float = 0.5f,
        val rotationAngle: Float = 0f,
        val isInverted: Boolean = false,
        val feather: Float = 0f,
        val positionKeyframes: List<KeyframePoint> = emptyList(),
        val sizeKeyframes: List<KeyframePoint> = emptyList(),
        val rotationKeyframes: List<KeyframePoint> = emptyList(),
        val featherKeyframes: List<KeyframePoint> = emptyList()
    ) : Serializable

    /**
     * Merge operation for combining multiple videos.
     */
    data class Merge(
        val items: List<MergeItem>,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    data class MergeItem(
        val uri: Uri,
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val speed: Float = 1f,
        val isMuted: Boolean = false,
        val maskConfig: MaskConfig = MaskConfig()
    ) : Serializable

    /** Mask for main track */
    data class MaskMain(
        val maskConfig: MaskConfig,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Mute audio operation.
     */
    data class MuteAudio(
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Transition between clips.
     */
    data class Transition(
        val index: Int,
        @SerializedName("transitionType") val type: String,
        val durationMs: Long = 1000L,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Mute specific clip.
     */
    data class MuteClip(
        val index: Int,
        val isMuted: Boolean,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Color filter (LUT) operation.
     */
    data class ColorFilter(
        val index: Int,
        val filterName: String,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Add background audio.
     */
    data class AddBackgroundAudio(
        val audioUri: Uri,
        val removeOriginalAudio: Boolean = false,
        val volume: Float = 1.0f,
        val internalStartMs: Long = 0L,
        val internalEndMs: Long = -1L,
        val startTimeMs: Long? = null,
        val endTimeMs: Long? = null,
        val originalDurationMs: Long = 0L,
        val extractedFromSegmentIndex: Int? = null,
        val beats: List<Long> = emptyList(),
        val ducking: Boolean = false,
        val fadeInDurationMs: Long = 0L,
        val fadeOutDurationMs: Long = 0L,
        val id: String = System.nanoTime().toString()
    ) : EditOperation()

    /**
     * Add image overlay.
     */
    data class AddImageOverlay(
        val imageUri: Uri,
        val relativeX: Float,
        val relativeY: Float,
        val relativeWidth: Float,
        val relativeHeight: Float,
        val rotationAngle: Float,
        val startTimeMs: Long? = null,
        val endTimeMs: Long? = null,
        val id: String = System.nanoTime().toString(),
        val fileDurationMs: Long? = null,
        val isLooping: Boolean = true,
        val chromaKeyColor: String? = null,
        val chromaKeySimilarity: Float = 0.1f,
        val opacity: Float = 1.0f,
        val isMirrored: Boolean = false,
        val positionKeyframes: List<KeyframePoint> = emptyList(),
        val opacityKeyframes: List<KeyframePoint> = emptyList(),
        val speedKeyframes: List<KeyframePoint> = emptyList(),
        val maskConfig: MaskConfig = MaskConfig()
    ) : EditOperation()

    /**
     * Add subtitles from SRT file.
     */
    data class AddSubtitles(
        val subtitlesUri: Uri,
        val srtContent: String,
        val fileName: String,
        val cues: List<SubtitleCue>,
        val color: String = "#FFFFFF",
        val backgroundColor: String = "none",
        val fontSize: Int = 22,
        val position: TextPosition = TextPosition.BOTTOM_CENTER,
        val relativeX: Float? = null,
        val relativeY: Float? = null,
        val id: String = System.nanoTime().toString()
    ) : EditOperation() {
        fun hasCustomPosition(): Boolean = relativeX != null && relativeY != null
    }

    /**
     * Adjust video properties.
     */
    data class Adjust(
        val index: Int,
        val brightness: Int = 0,
        val contrast: Int = 0,
        val warmth: Int = 0,
        val shadow: Int = 0,
        val highlights: Int = 0,
        val saturation: Int = 0,
        val exposure: Int = 0,
        val sharpen: Int = 0,
        val vignette: Int = 0,
        val id: String = System.nanoTime().toString()
    ) : EditOperation() {
        fun isDefault(): Boolean = brightness == 0 && contrast == 0 && warmth == 0 &&
            shadow == 0 && highlights == 0 && saturation == 0 && exposure == 0 &&
            sharpen == 0 && vignette == 0
    }

    /**
     * Canvas background.
     */
    data class CanvasBackground(
        @SerializedName("backgroundType") val type: BackgroundType = BackgroundType.COLOR,
        val colorHex: String = "#000000",
        val imageUri: Uri? = null,
        val blurRadius: Int = 20,
        val id: String = System.nanoTime().toString()
    ) : EditOperation() {
        enum class BackgroundType { COLOR, IMAGE, BLUR }
    }
}

data class SubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
) : java.io.Serializable

/**
 * Text position options.
 */
enum class TextPosition(val ffmpegParam: String) : Serializable {
    BOTTOM_RIGHT("x=w-tw:y=h-th"),
    TOP_RIGHT("x=w-tw:y=0"),
    TOP_LEFT("x=0:y=0"),
    BOTTOM_LEFT("x=0:y=h-th"),
    CENTER_BOTTOM("x=(w-text_w)/2:y=h-th"),
    CENTER_TOP("x=(w-text_w)/2:y=0"),
    CENTER("x=(w-text_w)/2:y=(h-text_h)/2"),
    TOP_CENTER("x=(w-text_w)/2:y=0"),
    CENTER_LEFT("x=0:y=(h-text_h)/2"),
    CENTER_RIGHT("x=w-tw:y=(h-text_h)/2"),
    BOTTOM_CENTER("x=(w-text_w)/2:y=h-th");

    companion object {
        fun fromLabel(label: String): TextPosition = when (label) {
            "Bottom Right" -> BOTTOM_RIGHT
            "Top Right" -> TOP_RIGHT
            "Top Left" -> TOP_LEFT
            "Bottom Left" -> BOTTOM_LEFT
            "Center Bottom" -> CENTER_BOTTOM
            "Center Top" -> CENTER_TOP
            "Center Align" -> CENTER
            "Top Center" -> TOP_CENTER
            "Center Left" -> CENTER_LEFT
            "Center Right" -> CENTER_RIGHT
            "Bottom Center" -> BOTTOM_CENTER
            else -> CENTER
        }

        fun labels() = listOf(
            "Top Left", "Top Center", "Top Right",
            "Center Left", "Center Align", "Center Right",
            "Bottom Left", "Bottom Center", "Bottom Right"
        )
    }
}

val EditOperation.operationId: String
    get() = when (this) {
        is EditOperation.Trim -> this.id
        is EditOperation.SpeedMain -> this.id
        is EditOperation.ReverseMain -> this.id
        is EditOperation.MirrorMain -> this.id
        is EditOperation.MaskMain -> this.id
        is EditOperation.Crop -> this.id
        is EditOperation.AddText -> this.id
        is EditOperation.Merge -> this.id
        is EditOperation.MuteAudio -> this.id
        is EditOperation.Transition -> this.id
        is EditOperation.MuteClip -> this.id
        is EditOperation.ColorFilter -> this.id
        is EditOperation.AddBackgroundAudio -> this.id
        is EditOperation.AddImageOverlay -> this.id
        is EditOperation.AddSubtitles -> this.id
        is EditOperation.Adjust -> this.id
        is EditOperation.CanvasBackground -> this.id
    }
