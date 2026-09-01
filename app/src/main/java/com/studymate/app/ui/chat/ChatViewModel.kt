package com.studymate.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studymate.app.StudyMateApp
import com.studymate.app.data.SettingsManager
import com.studymate.app.llm.LlmManager
import com.studymate.app.llm.ModelFileInfo
import com.studymate.app.llm.ModelLoader
import com.studymate.app.rag.PromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the ChatGPT-inspired offline conversational interface.
 */
class ChatViewModel(
    private val llm: LlmManager = StudyMateApp.instance.llmManager,
    private val settings: SettingsManager = StudyMateApp.instance.settingsManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _modelAvailable = MutableStateFlow(llm.isModelAvailable())
    val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

    private val _modelName = MutableStateFlow(llm.getActiveModelDisplayName())
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _availableModels = MutableStateFlow<List<ModelFileInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelFileInfo>> = _availableModels.asStateFlow()

    private val _isImportingModel = MutableStateFlow(false)
    val isImportingModel: StateFlow<Boolean> = _isImportingModel.asStateFlow()

    private val _importStatus = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        val context = StudyMateApp.instance
        val selected = settings.selectedModelName.value
        _modelAvailable.value = llm.isModelAvailable()
        _modelName.value = llm.getActiveModelDisplayName()
        _availableModels.value = ModelLoader.getAvailableModels(context, selected)
    }

    fun selectModel(fileName: String?) {
        settings.setSelectedModel(fileName)
        llm.unload()
        refreshModels()
    }

    fun sendQuestion(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _isGenerating.value) return

        val userMsg = ChatMessage(ChatMessage.nextId(), ChatMessage.Role.USER, q)
        val assistantMsg = ChatMessage(
            id = ChatMessage.nextId(),
            role = ChatMessage.Role.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _messages.update { it + listOf(userMsg, assistantMsg) }
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val prompt = PromptBuilder.chatPrompt(q, settings.systemInstruction.value)
                llm.generateStream(prompt) { token ->
                    appendToLast(token)
                }
                finalizeLast(isError = false)
            } catch (e: Exception) {
                replaceLast("Error: ${e.message ?: "Generation encountered an issue."}", isError = true)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun regenerateLast() {
        if (_isGenerating.value) return
        val list = _messages.value
        if (list.isEmpty()) return

        // Find last user question
        val lastUserMsg = list.lastOrNull { it.role == ChatMessage.Role.USER } ?: return
        
        // Remove trailing assistant response if any
        if (list.last().role == ChatMessage.Role.ASSISTANT) {
            _messages.update { it.dropLast(1) }
        }

        val assistantMsg = ChatMessage(
            id = ChatMessage.nextId(),
            role = ChatMessage.Role.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _messages.update { it + assistantMsg }
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val prompt = PromptBuilder.chatPrompt(lastUserMsg.content, settings.systemInstruction.value)
                llm.generateStream(prompt) { token ->
                    appendToLast(token)
                }
                finalizeLast(isError = false)
            } catch (e: Exception) {
                replaceLast("Error: ${e.message ?: "Generation encountered an issue."}", isError = true)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun importModel(uri: Uri, displayName: String) {
        val context = StudyMateApp.instance
        viewModelScope.launch {
            _isImportingModel.value = true
            _importStatus.value = "Importing $displayName…"
            try {
                ModelLoader.importModelFromUri(context, uri, displayName) { copied, total ->
                    val progressText = if (total > 0) {
                        "${ModelLoader.formatBytes(copied)} / ${ModelLoader.formatBytes(total)}"
                    } else {
                        ModelLoader.formatBytes(copied)
                    }
                    _importStatus.value = "Copying model file: $progressText"
                }
                selectModel(displayName)
                refreshModels()
                _importStatus.value = "Model imported successfully!"
                _messages.update {
                    it + ChatMessage(
                        ChatMessage.nextId(),
                        ChatMessage.Role.ASSISTANT,
                        "Model **$displayName** imported and activated successfully! You can now run local offline inference."
                    )
                }
            } catch (e: Exception) {
                _importStatus.value = "Failed: ${e.message}"
            } finally {
                _isImportingModel.value = false
            }
        }
    }

    fun deleteModel(fileName: String) {
        val context = StudyMateApp.instance
        ModelLoader.deleteModel(context, fileName)
        if (settings.selectedModelName.value == fileName) {
            settings.setSelectedModel(null)
        }
        llm.unload()
        refreshModels()
    }

    fun clearChat() {
        if (_isGenerating.value) return
        _messages.value = emptyList()
    }

    private fun appendToLast(token: String) {
        _messages.update { list ->
            list.toMutableList().also { mutable ->
                val last = mutable.lastOrNull() ?: return@also
                mutable[mutable.lastIndex] = last.copy(content = last.content + token)
            }
        }
    }

    private fun finalizeLast(isError: Boolean) {
        _messages.update { list ->
            list.toMutableList().also { mutable ->
                val last = mutable.lastOrNull() ?: return@also
                mutable[mutable.lastIndex] = last.copy(isStreaming = false, isError = isError)
            }
        }
    }

    private fun replaceLast(text: String, isError: Boolean) {
        _messages.update { list ->
            list.toMutableList().also { mutable ->
                val last = mutable.lastOrNull() ?: return@also
                mutable[mutable.lastIndex] = last.copy(
                    content = text, isStreaming = false, isError = isError
                )
            }
        }
    }
}
