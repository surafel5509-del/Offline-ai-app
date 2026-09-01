package com.studymate.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studymate.app.StudyMateApp
import com.studymate.app.data.DocumentRepository
import com.studymate.app.data.SettingsManager
import com.studymate.app.llm.BenchmarkResult
import com.studymate.app.llm.LlmManager
import com.studymate.app.llm.ModelFileInfo
import com.studymate.app.llm.ModelLoader
import com.studymate.app.util.IoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: String = SettingsManager.THEME_SYSTEM,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val topK: Int = 40,
    val systemInstruction: String = SettingsManager.DEFAULT_SYSTEM_INSTRUCTION,
    val similarityThreshold: Float = 0.35f,
    val activeModelName: String = "Built-in Neural Engine",
    val availableModels: List<ModelFileInfo> = emptyList(),
    val totalModelsSizeFormatted: String = "0 B",
    val totalDocsCount: Int = 0,
    val totalChunksCount: Int = 0,
    val isRunningBenchmark: Boolean = false,
    val benchmarkResult: BenchmarkResult? = null,
    val isImporting: Boolean = false,
    val importStatus: String = "",
    val message: String? = null
)

/**
 * ViewModel for application preferences, local model management, and inference tweaking.
 */
class SettingsViewModel(
    private val settings: SettingsManager = StudyMateApp.instance.settingsManager,
    private val llm: LlmManager = StudyMateApp.instance.llmManager,
    private val repository: DocumentRepository = StudyMateApp.instance.repository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val context = StudyMateApp.instance
        viewModelScope.launch {
            val docs = repository.getAllDocuments()
            val totalChunks = docs.sumOf { it.chunkCount }
            val models = ModelLoader.getAvailableModels(context, settings.selectedModelName.value)
            val totalBytes = ModelLoader.getTotalModelsSizeBytes(context)

            _state.update {
                it.copy(
                    themeMode = settings.themeMode.value,
                    temperature = settings.temperature.value,
                    maxTokens = settings.maxTokens.value,
                    topK = settings.topK.value,
                    systemInstruction = settings.systemInstruction.value,
                    similarityThreshold = settings.similarityThreshold.value,
                    activeModelName = llm.getActiveModelDisplayName(),
                    availableModels = models,
                    totalModelsSizeFormatted = ModelLoader.formatBytes(totalBytes),
                    totalDocsCount = docs.size,
                    totalChunksCount = totalChunks
                )
            }
        }
    }

    fun setThemeMode(mode: String) {
        settings.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    fun setTemperature(temp: Float) {
        settings.setTemperature(temp)
        _state.update { it.copy(temperature = temp) }
    }

    fun setMaxTokens(tokens: Int) {
        settings.setMaxTokens(tokens)
        _state.update { it.copy(maxTokens = tokens) }
    }

    fun setTopK(k: Int) {
        settings.setTopK(k)
        _state.update { it.copy(topK = k) }
    }

    fun setSystemInstruction(prompt: String) {
        settings.setSystemInstruction(prompt)
        _state.update { it.copy(systemInstruction = prompt) }
    }

    fun setSimilarityThreshold(threshold: Float) {
        settings.setSimilarityThreshold(threshold)
        _state.update { it.copy(similarityThreshold = threshold) }
    }

    fun selectModel(fileName: String?) {
        settings.setSelectedModel(fileName)
        llm.unload()
        loadSettings()
    }

    fun deleteModel(fileName: String) {
        val context = StudyMateApp.instance
        ModelLoader.deleteModel(context, fileName)
        if (settings.selectedModelName.value == fileName) {
            settings.setSelectedModel(null)
        }
        llm.unload()
        loadSettings()
    }

    fun importModel(uri: Uri) {
        val context = StudyMateApp.instance
        val name = IoUtils.displayName(context, uri)

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importStatus = "Importing $name…") }
            try {
                ModelLoader.importModelFromUri(context, uri, name) { copied, total ->
                    val status = if (total > 0) {
                        "${ModelLoader.formatBytes(copied)} / ${ModelLoader.formatBytes(total)}"
                    } else {
                        ModelLoader.formatBytes(copied)
                    }
                    _state.update { it.copy(importStatus = "Transferring: $status") }
                }
                settings.setSelectedModel(name)
                llm.unload()
                loadSettings()
                _state.update { it.copy(isImporting = false, message = "Model $name activated successfully!") }
            } catch (e: Exception) {
                _state.update { it.copy(isImporting = false, message = "Import failed: ${e.message}") }
            }
        }
    }

    fun runBenchmark() {
        viewModelScope.launch {
            _state.update { it.copy(isRunningBenchmark = true, benchmarkResult = null) }
            try {
                val result = llm.runBenchmark()
                _state.update { it.copy(isRunningBenchmark = false, benchmarkResult = result) }
            } catch (e: Exception) {
                _state.update { it.copy(isRunningBenchmark = false, message = "Benchmark error: ${e.message}") }
            }
        }
    }

    fun clearAllDocuments() {
        viewModelScope.launch {
            val docs = repository.getAllDocuments()
            docs.forEach { repository.deleteDocument(it) }
            loadSettings()
            _state.update { it.copy(message = "All documents and vector indices wiped.") }
        }
    }

    fun resetToDefaults() {
        settings.resetToDefaults()
        llm.unload()
        loadSettings()
        _state.update { it.copy(message = "Settings reset to factory defaults.") }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
