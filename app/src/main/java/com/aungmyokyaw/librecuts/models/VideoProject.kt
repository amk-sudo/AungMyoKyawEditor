package com.aungmyokyaw.librecuts.models

import android.net.Uri
import java.io.Serializable

/**
 * VideoProject represents the complete state of a video editing session.
 */
data class VideoProject(
    val sourceUri: Uri,
    val sourceName: String,
    val operations: List<EditOperation> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
) : Serializable {

    fun getDurationAfterTrims(): Long? {
        for (operation in operations) {
            if (operation is EditOperation.Trim) {
                return operation.endMs - operation.startMs
            }
        }
        return null
    }

    fun addOperation(operation: EditOperation): VideoProject {
        return copy(
            operations = operations + operation,
            lastModifiedAt = System.currentTimeMillis()
        )
    }

    fun undoLastOperation(): VideoProject? {
        if (operations.isEmpty()) return null
        return copy(
            operations = operations.dropLast(1),
            lastModifiedAt = System.currentTimeMillis()
        )
    }

    fun removeOperationsOfType(operationType: Class<out EditOperation>): VideoProject {
        return copy(
            operations = operations.filterNot { it::class.java == operationType },
            lastModifiedAt = System.currentTimeMillis()
        )
    }

    fun hasOperations(): Boolean = operations.isNotEmpty()
    fun getOperationCount(): Int = operations.size
    fun getOperationCount(operationType: Class<out EditOperation>): Int {
        return operations.count { it::class.java == operationType }
    }
}

/**
 * EditRecipe for persistent project storage.
 */
data class EditRecipe(
    val projectName: String,
    val sourceUri: Uri,
    val sourceName: String,
    val operations: List<EditOperation> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
) : Serializable {

    fun toVideoProject(): VideoProject {
        return VideoProject(
            sourceUri = sourceUri,
            sourceName = sourceName,
            operations = operations,
            createdAt = createdAt,
            lastModifiedAt = lastModifiedAt
        )
    }

    companion object {
        fun fromVideoProject(projectName: String, project: VideoProject): EditRecipe {
            return EditRecipe(
                projectName = projectName,
                sourceUri = project.sourceUri,
                sourceName = project.sourceName,
                operations = project.operations,
                createdAt = project.createdAt,
                lastModifiedAt = project.lastModifiedAt
            )
        }
    }
}

/**
 * UI state for video editing session.
 */
data class VideoEditingUiState(
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val errorMessage: String? = null,
    val pendingOperationCount: Int = 0,
    val currentPreviewOperationIndex: Int = -1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

/**
 * Export configuration.
 */
data class ExportConfig(
    val outputPath: String,
    val videoCodec: String = "libx264",
    val audioCodec: String = "aac",
    val bitrate: String = "2M",
    val preset: String = "medium",
    val frameRate: String = "30"
)
