package com.studymate.app.ui.study

import com.studymate.app.data.DocumentEntity
import com.studymate.app.rag.Flashcard
import com.studymate.app.rag.QuizItem
import com.studymate.app.rag.RetrievedChunk

enum class StudySubTab {
    QA,
    SUMMARY,
    FLASHCARDS,
    QUIZ
}

/**
 * Immutable UI state for the "Study Assistant" (RAG) tab.
 */
data class StudyUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val selectedDocumentId: Long? = null,
    val selectedSubTab: StudySubTab = StudySubTab.QA,
    val isIndexing: Boolean = false,
    val indexStage: String = "",
    val isAnswering: Boolean = false,
    val answer: String = "",
    val retrievedChunks: List<RetrievedChunk> = emptyList(),
    val showSources: Boolean = false,
    val isGeneratingSummary: Boolean = false,
    val summary: String = "",
    val isGeneratingFlashcards: Boolean = false,
    val flashcards: List<Flashcard> = emptyList(),
    val currentCardIndex: Int = 0,
    val isCardFlipped: Boolean = false,
    val masteredCardIds: Set<Int> = emptySet(),
    val isGeneratingQuiz: Boolean = false,
    val quizQuestions: List<QuizItem> = emptyList(),
    val quizSelectedAnswers: Map<Int, Int> = emptyMap(),
    val error: String? = null
)
