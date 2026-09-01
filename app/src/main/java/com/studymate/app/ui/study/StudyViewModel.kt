package com.studymate.app.ui.study

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studymate.app.StudyMateApp
import com.studymate.app.data.DocumentEntity
import com.studymate.app.data.DocumentRepository
import com.studymate.app.rag.RagService
import com.studymate.app.util.IoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the comprehensive Study Assistant workspace.
 */
class StudyViewModel(
    private val repository: DocumentRepository = StudyMateApp.instance.repository,
    private val ragService: RagService = StudyMateApp.instance.ragService
) : ViewModel() {

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()

    init {
        loadDocuments()
    }

    fun loadDocuments() {
        viewModelScope.launch {
            val docs = repository.getAllDocuments()
            _state.update {
                it.copy(
                    documents = docs,
                    selectedDocumentId = it.selectedDocumentId ?: docs.firstOrNull()?.id
                )
            }
        }
    }

    fun selectDocument(id: Long) {
        _state.update {
            it.copy(
                selectedDocumentId = id,
                answer = "",
                retrievedChunks = emptyList(),
                showSources = false,
                summary = "",
                flashcards = emptyList(),
                quizQuestions = emptyList(),
                quizSelectedAnswers = emptyMap(),
                currentCardIndex = 0,
                isCardFlipped = false
            )
        }
    }

    fun setSubTab(tab: StudySubTab) {
        _state.update { it.copy(selectedSubTab = tab) }
        val docId = _state.value.selectedDocumentId ?: return
        when (tab) {
            StudySubTab.SUMMARY -> if (_state.value.summary.isBlank()) generateSummary(docId)
            StudySubTab.FLASHCARDS -> if (_state.value.flashcards.isEmpty()) generateFlashcards(docId)
            StudySubTab.QUIZ -> if (_state.value.quizQuestions.isEmpty()) generateQuiz(docId)
            else -> Unit
        }
    }

    fun indexFile(uri: Uri) {
        val context = StudyMateApp.instance
        val name = IoUtils.displayName(context, uri)
        val mime = IoUtils.mimeType(context, uri)

        viewModelScope.launch {
            _state.update { it.copy(isIndexing = true, indexStage = "Reading document…", error = null) }
            try {
                val id = ragService.indexDocument(uri, name, mime) { progress ->
                    _state.update {
                        it.copy(indexStage = "${progress.stage} (${progress.value}/${progress.total})")
                    }
                }
                val docs = repository.getAllDocuments()
                _state.update {
                    it.copy(
                        documents = docs,
                        selectedDocumentId = id,
                        isIndexing = false,
                        indexStage = "Completed"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isIndexing = false, indexStage = "", error = e.message ?: "Document indexing failed")
                }
            }
        }
    }

    fun askQuestion(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val docId = _state.value.selectedDocumentId ?: return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isAnswering = true,
                    answer = "",
                    retrievedChunks = emptyList(),
                    showSources = false,
                    error = null
                )
            }
            try {
                val result = ragService.answerQuestion(docId, q)
                _state.update {
                    it.copy(
                        isAnswering = false,
                        answer = result.answer,
                        retrievedChunks = result.sources,
                        showSources = result.sources.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isAnswering = false, error = e.message ?: "Could not generate answer")
                }
            }
        }
    }

    fun generateSummary(documentId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingSummary = true, error = null) }
            try {
                val summaryText = ragService.generateSummary(documentId)
                _state.update { it.copy(isGeneratingSummary = false, summary = summaryText) }
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingSummary = false, error = e.message ?: "Failed generating summary") }
            }
        }
    }

    fun generateFlashcards(documentId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingFlashcards = true, error = null) }
            try {
                val cards = ragService.generateFlashcards(documentId)
                _state.update {
                    it.copy(
                        isGeneratingFlashcards = false,
                        flashcards = cards,
                        currentCardIndex = 0,
                        isCardFlipped = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingFlashcards = false, error = e.message ?: "Failed creating flashcards") }
            }
        }
    }

    fun generateQuiz(documentId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingQuiz = true, error = null) }
            try {
                val quiz = ragService.generateQuiz(documentId)
                _state.update {
                    it.copy(
                        isGeneratingQuiz = false,
                        quizQuestions = quiz,
                        quizSelectedAnswers = emptyMap()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingQuiz = false, error = e.message ?: "Failed generating quiz") }
            }
        }
    }

    fun selectQuizAnswer(quizId: Int, optionIndex: Int) {
        _state.update {
            val updated = it.quizSelectedAnswers.toMutableMap()
            updated[quizId] = optionIndex
            it.copy(quizSelectedAnswers = updated)
        }
    }

    fun flipCard() {
        _state.update { it.copy(isCardFlipped = !it.isCardFlipped) }
    }

    fun toggleMasteredCard(cardId: Int) {
        _state.update {
            val updated = it.masteredCardIds.toMutableSet()
            if (updated.contains(cardId)) updated.remove(cardId) else updated.add(cardId)
            it.copy(masteredCardIds = updated)
        }
    }

    fun nextCard() {
        _state.update {
            if (it.flashcards.isNotEmpty()) {
                val nextIdx = (it.currentCardIndex + 1) % it.flashcards.size
                it.copy(currentCardIndex = nextIdx, isCardFlipped = false)
            } else it
        }
    }

    fun prevCard() {
        _state.update {
            if (it.flashcards.isNotEmpty()) {
                val prevIdx = if (it.currentCardIndex - 1 < 0) it.flashcards.size - 1 else it.currentCardIndex - 1
                it.copy(currentCardIndex = prevIdx, isCardFlipped = false)
            } else it
        }
    }

    fun shuffleFlashcards() {
        _state.update {
            it.copy(
                flashcards = it.flashcards.shuffled(),
                currentCardIndex = 0,
                isCardFlipped = false
            )
        }
    }

    fun toggleShowSources() {
        _state.update { it.copy(showSources = !it.showSources) }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            loadDocuments()
            if (_state.value.selectedDocumentId == doc.id) {
                _state.update {
                    it.copy(
                        selectedDocumentId = null,
                        answer = "",
                        retrievedChunks = emptyList(),
                        summary = "",
                        flashcards = emptyList(),
                        quizQuestions = emptyList()
                    )
                }
            }
        }
    }

    fun clearAnswer() {
        _state.update { it.copy(answer = "", retrievedChunks = emptyList(), showSources = false) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
