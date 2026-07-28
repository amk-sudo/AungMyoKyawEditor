package com.aungmyokyaw.librecuts.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aungmyokyaw.librecuts.models.EditOperation
import com.aungmyokyaw.librecuts.models.EditRecipe
import com.aungmyokyaw.librecuts.models.TextPosition
import com.aungmyokyaw.librecuts.models.VideoEditingUiState
import com.aungmyokyaw.librecuts.models.VideoProject
import com.aungmyokyaw.librecuts.models.operationId
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class VideoEditingViewModel : ViewModel() {

    private companion object {
        const val TAG = "VideoEditingViewModel"
        const val MAX_UNDO_STACK_SIZE = 30
    }

    private fun pushUndoState(current: VideoProject) {
        val stack = _undoStack.value + current
        _undoStack.value = if (stack.size > MAX_UNDO_STACK_SIZE) stack.takeLast(MAX_UNDO_STACK_SIZE) else stack
        _redoStack.value = emptyList()
    }

    private val _project = MutableStateFlow<VideoProject?>(null)
    private val _uiState = MutableStateFlow(VideoEditingUiState())
    private val _undoStack = MutableStateFlow<List<VideoProject>>(emptyList())
    private val _redoStack = MutableStateFlow<List<VideoProject>>(emptyList())
    private val _exportResolution = MutableStateFlow(1080)
    private val _exportFps = MutableStateFlow(30)
    private val _exportAudioOnly = MutableStateFlow(false)
    private val _selectedOperationId = MutableStateFlow<String?>(null)
    private val _hasUnsavedEdits = MutableStateFlow(false)

    val project: StateFlow<VideoProject?> = _project.asStateFlow()
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()
    val selectedOperationId: StateFlow<String?> = _selectedOperationId.asStateFlow()
    val undoStack: StateFlow<List<VideoProject>> = _undoStack.asStateFlow()
    val redoStack: StateFlow<List<VideoProject>> = _redoStack.asStateFlow()
    val exportResolution: StateFlow<Int> = _exportResolution.asStateFlow()
    val exportFps: StateFlow<Int> = _exportFps.asStateFlow()
    val exportAudioOnly: StateFlow<Boolean> = _exportAudioOnly.asStateFlow()
    val hasUnsavedEdits: StateFlow<Boolean> = _hasUnsavedEdits.asStateFlow()

    val operations: StateFlow<List<EditOperation>>
        get() = MutableStateFlow(project.value?.operations ?: emptyList()).asStateFlow()

    fun initializeProject(sourceUri: Uri, sourceName: String) {
        _project.value = VideoProject(sourceUri = sourceUri, sourceName = sourceName)
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        _hasUnsavedEdits.value = false
        updateUiState { it.copy(canUndo = false) }
    }

    fun loadProject(project: VideoProject) {
        _project.value = project
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        _hasUnsavedEdits.value = false
        updateUiState { it.copy(canUndo = false, pendingOperationCount = project.getOperationCount()) }
    }

    fun updateMainVideoTrim(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            _project.update { current ->
                if (current == null) return@update null
                val ops = current.operations.toMutableList()
                val trimIndex = ops.indexOfFirst { it is EditOperation.Trim }
                if (trimIndex != -1) {
                    ops[trimIndex] = EditOperation.Trim(startMs, endMs)
                } else {
                    ops.add(EditOperation.Trim(startMs, endMs))
                }
                pushUndoState(current)
                current.copy(operations = ops)
            }
            updateUiState { state ->
                state.copy(
                    pendingOperationCount = _project.value?.getOperationCount() ?: 0,
                    canUndo = _undoStack.value.isNotEmpty()
                )
            }
        }
    }

    fun updateMainVideoSpeed(speed: Float, proxyUri: Uri?) {
        viewModelScope.launch {
            _project.update { current ->
                if (current == null) return@update null
                val ops = current.operations.toMutableList()
                val speedIndex = ops.indexOfFirst { it is EditOperation.SpeedMain }
                if (speedIndex != -1) {
                    ops[speedIndex] = EditOperation.SpeedMain(speed, proxyUri)
                } else {
                    ops.add(EditOperation.SpeedMain(speed, proxyUri))
                }
                pushUndoState(current)
                current.copy(operations = ops)
            }
            updateUiState { it.copy(canUndo = _undoStack.value.isNotEmpty()) }
        }
    }

    fun updateMainVideoReverse(isReversed: Boolean, proxyUri: Uri?) {
        viewModelScope.launch {
            _project.update { current ->
                if (current == null) return@update null
                val ops = current.operations.toMutableList()
                val reverseIndex = ops.indexOfFirst { it is EditOperation.ReverseMain }
                if (reverseIndex != -1) {
                    ops[reverseIndex] = EditOperation.ReverseMain(isReversed, proxyUri)
                } else {
                    ops.add(EditOperation.ReverseMain(isReversed, proxyUri))
                }
                pushUndoState(current)
                current.copy(operations = ops)
            }
            updateUiState { it.copy(canUndo = _undoStack.value.isNotEmpty()) }
        }
    }

    fun updateMainVideoMirror(isMirrored: Boolean) {
        viewModelScope.launch {
            _project.update { current ->
                if (current == null) return@update null
                val ops = current.operations.toMutableList()
                val mirrorIndex = ops.indexOfFirst { it is EditOperation.MirrorMain }
                if (mirrorIndex != -1) {
                    ops[mirrorIndex] = EditOperation.MirrorMain(isMirrored)
                } else {
                    ops.add(EditOperation.MirrorMain(isMirrored))
                }
                pushUndoState(current)
                current.copy(operations = ops)
            }
            updateUiState { it.copy(canUndo = _undoStack.value.isNotEmpty()) }
        }
    }

    fun addOperation(operation: EditOperation) {
        viewModelScope.launch {
            _project.update { current ->
                if (current == null) return@update null
                pushUndoState(current)
                current.addOperation(operation)
            }
            updateUiState { state ->
                state.copy(
                    pendingOperationCount = _project.value?.getOperationCount() ?: 0,
                    canUndo = _undoStack.value.isNotEmpty()
                )
            }
        }
    }

    fun updateOperation(operation: EditOperation) {
        viewModelScope.launch {
            _project.update { current ->
                if (current == null) return@update null
                val ops = current.operations.toMutableList()
                val index = ops.indexOfFirst { it.operationId == operation.operationId }
                if (index != -1) {
                    ops[index] = operation
                }
                current.copy(operations = ops)
            }
        }
    }

    fun removeOperation(operationId: String) {
        viewModelScope.launch {
            _project.update { current ->
                if (current == null) return@update null
                val ops = current.operations.filterNot { it.operationId == operationId }
                current.copy(operations = ops)
            }
            updateUiState { it.copy(pendingOperationCount = _project.value?.getOperationCount() ?: 0) }
        }
    }

    fun undo() {
        viewModelScope.launch {
            if (_undoStack.value.isEmpty()) return@launch
            val previousState = _undoStack.value.last()
            _redoStack.value = _redoStack.value + (_project.value ?: return@launch)
            _undoStack.value = _undoStack.value.dropLast(1)
            _project.value = previousState
            updateUiState { state ->
                state.copy(
                    pendingOperationCount = previousState.getOperationCount(),
                    canUndo = _undoStack.value.isNotEmpty(),
                    canRedo = true
                )
            }
        }
    }

    fun redo() {
        viewModelScope.launch {
            if (_redoStack.value.isEmpty()) return@launch
            val nextState = _redoStack.value.last()
            _undoStack.value = _undoStack.value + (_project.value ?: return@launch)
            _redoStack.value = _redoStack.value.dropLast(1)
            _project.value = nextState
            updateUiState { state ->
                state.copy(
                    pendingOperationCount = nextState.getOperationCount(),
                    canUndo = true,
                    canRedo = _redoStack.value.isNotEmpty()
                )
            }
        }
    }

    fun setExportSettings(resolution: Int, fps: Int, audioOnly: Boolean) {
        _exportResolution.value = resolution
        _exportFps.value = fps
        _exportAudioOnly.value = audioOnly
    }

    fun markProjectSaved() {
        _hasUnsavedEdits.value = false
    }

    fun markHasUnsavedEdits() {
        _hasUnsavedEdits.value = true
    }

    fun saveRecipe(projectName: String): EditRecipe? =
        _project.value?.let { EditRecipe.fromVideoProject(projectName, it) }

    fun loadRecipe(recipe: EditRecipe) {
        _project.value = recipe.toVideoProject()
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        updateUiState { it.copy(pendingOperationCount = recipe.operations.size, canUndo = false) }
    }

    fun saveRecipeToFile(context: Context, recipe: EditRecipe, uri: Uri): Boolean {
        return try {
            val json = Gson().toJson(recipe)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save recipe: ${e.message}")
            false
        }
    }

    fun loadRecipeFromFile(context: Context, uri: Uri): EditRecipe? {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            json?.let { Gson().fromJson(it, EditRecipe::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load recipe: ${e.message}")
            null
        }
    }

    private fun updateUiState(updater: (VideoEditingUiState) -> VideoEditingUiState) {
        _uiState.update(updater)
        if (_undoStack.value.isNotEmpty()) {
            _hasUnsavedEdits.value = true
        }
    }

    fun startExport() {
        updateUiState { it.copy(isExporting = true, exportProgress = 0, errorMessage = null) }
    }

    fun updateExportProgress(progress: Int) {
        updateUiState { it.copy(exportProgress = progress.coerceIn(0, 100)) }
    }

    fun finishExport() {
        updateUiState { it.copy(isExporting = false, exportProgress = 100) }
    }

    fun exportError(error: String) {
        updateUiState { it.copy(isExporting = false, errorMessage = error) }
    }

    fun clearError() {
        updateUiState { it.copy(errorMessage = null) }
    }
}
