package com.studymate.app.rag

import android.net.Uri
import com.studymate.app.data.ChunkEntity
import com.studymate.app.data.DocumentEntity
import com.studymate.app.data.DocumentRepository
import com.studymate.app.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interactive flashcard item extracted from document content.
 */
data class Flashcard(
    val id: Int,
    val question: String,
    val answer: String,
    val topicTag: String,
    val sourceChunkOrdinal: Int
)

/**
 * Interactive quiz question with options and explanation.
 */
data class QuizItem(
    val id: Int,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

/**
 * Orchestrates the full offline RAG pipeline and automated study tools:
 * - Indexing: Extract text (PDF OCR / TXT) -> sentence-aware chunking -> embedding -> Room BLOB vector persistence
 * - Vector Search: Dense cosine similarity query retrieval (Top-K)
 * - Q&A: Local LLM generation with retrieved chunk context
 * - Summarizer: Automated multi-perspective synthesis
 * - Flashcards: Concept & definition extraction
 * - Practice Quiz: Interactive self-testing engine
 */
class RagService(
    private val repository: DocumentRepository,
    private val extractor: TextExtractor,
    private val embedder: EmbeddingManager,
    private val llm: LlmManager
) {
    private val chunker = TextChunker()

    data class IndexProgress(val stage: String, val value: Int, val total: Int)

    /**
     * Index a document end-to-end. Returns the created [DocumentEntity] id.
     */
    suspend fun indexDocument(
        uri: Uri,
        displayName: String,
        mimeType: String,
        onProgress: (IndexProgress) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        // 1) Create document row
        val doc = DocumentEntity(displayName = displayName, uri = uri.toString(), mimeType = mimeType)
        val docId = repository.insertDocument(doc)

        try {
            // 2) Extract text via ML Kit OCR or text stream
            onProgress(IndexProgress("Extracting document text", 0, 0))
            val text = extractor.extract(uri, mimeType) { page, total ->
                onProgress(IndexProgress("Processing page $page of $total", page, total))
            }
            if (text.isBlank()) {
                throw IllegalStateException("No readable text found in this document.")
            }

            // 3) Chunk with sentence awareness
            onProgress(IndexProgress("Chunking document sections", 0, 0))
            val chunks = chunker.chunk(text)
            if (chunks.isEmpty()) throw IllegalStateException("Document could not be segmented.")

            // 4) Embed each chunk into dense vector space
            val entities = ArrayList<ChunkEntity>(chunks.size)
            chunks.forEachIndexed { index, chunk ->
                val embedding = embedder.embed(chunk.text) ?: FloatArray(0)
                entities.add(
                    ChunkEntity(
                        documentId = docId,
                        ordinal = chunk.ordinal,
                        text = chunk.text,
                        embedding = embedding
                    )
                )
                onProgress(IndexProgress("Generating vector embeddings", index + 1, chunks.size))
            }

            // 5) Persist to Room vector store
            repository.replaceChunks(docId, entities)

            // 6) Update document entity stats
            repository.updateDocument(
                doc.copy(id = docId, charCount = text.length, chunkCount = entities.size)
            )
            docId
        } catch (e: Exception) {
            repository.getDocument(docId)?.let { repository.deleteDocument(it) }
            throw e
        }
    }

    /**
     * Answer a question against an indexed document using dense vector search & LLM.
     */
    suspend fun answerQuestion(
        documentId: Long,
        question: String,
        minScore: Float = 0.30f
    ): RagAnswer = withContext(Dispatchers.IO) {
        val chunks = repository.loadChunks(documentId)
        if (chunks.isEmpty()) {
            return@withContext RagAnswer(
                answer = "This document is not yet indexed in local storage.",
                sources = emptyList()
            )
        }

        val queryEmbedding = embedder.embed(question)
        val sources: List<RetrievedChunk> = if (queryEmbedding != null && queryEmbedding.isNotEmpty()) {
            VectorRetriever(chunks).retrieve(queryEmbedding, k = 4, minScore = minScore)
                .ifEmpty { chunks.take(3) }
        } else {
            chunks.take(3)
        }

        val prompt = PromptBuilder.ragPrompt(question, sources.map { it.text })
        val answer = llm.generate(prompt)
        RagAnswer(answer = answer.trim(), sources = sources)
    }

    /**
     * Generates a structured multi-part study summary.
     */
    suspend fun generateSummary(documentId: Long): String = withContext(Dispatchers.IO) {
        val doc = repository.getDocument(documentId)
        val chunks = repository.loadChunks(documentId)
        if (chunks.isEmpty()) return@withContext "Document contains no extractable text."

        val sampleChunks = if (chunks.size <= 5) chunks else {
            listOf(chunks.first()) + chunks.takeLast(chunks.size / 2).take(3) + listOf(chunks.last())
        }
        val sampleText = sampleChunks.joinToString("\n\n---\n\n") { it.text }
        val prompt = PromptBuilder.summaryPrompt(doc?.displayName ?: "Study Document", sampleText)
        llm.generate(prompt)
    }

    /**
     * Generates interactive flashcards from document chunks.
     */
    suspend fun generateFlashcards(documentId: Long): List<Flashcard> = withContext(Dispatchers.IO) {
        val chunks = repository.loadChunks(documentId)
        if (chunks.isEmpty()) return@withContext emptyList()

        val cards = mutableListOf<Flashcard>()
        var cardId = 1

        for ((_, chunk) in chunks.take(10).withIndex()) {
            val sentences = chunk.text.split(Regex("[.!?\\n]\\s*")).filter { it.trim().length > 25 }
            for (sentence in sentences.take(2)) {
                val clean = sentence.trim()
                val words = clean.split(" ")
                if (words.size >= 6) {
                    val promptTerm = words.take(4).joinToString(" ")
                    val topic = if (words.size > 2) words[0].replace(Regex("[^a-zA-Z]"), "") else "Concept"
                    cards.add(
                        Flashcard(
                            id = cardId++,
                            question = "Explain the key significance of \"$promptTerm...\" in this topic:",
                            answer = clean,
                            topicTag = topic.ifBlank { "Core" },
                            sourceChunkOrdinal = chunk.ordinal
                        )
                    )
                    if (cards.size >= 12) break
                }
            }
            if (cards.size >= 12) break
        }

        if (cards.isEmpty()) {
            cards.add(
                Flashcard(
                    id = 1,
                    question = "Core Study Concept Overview",
                    answer = chunks.first().text.take(300),
                    topicTag = "Overview",
                    sourceChunkOrdinal = 0
                )
            )
        }
        cards
    }

    /**
     * Generates interactive practice quiz questions from document chunks.
     */
    suspend fun generateQuiz(documentId: Long): List<QuizItem> = withContext(Dispatchers.IO) {
        val chunks = repository.loadChunks(documentId)
        if (chunks.isEmpty()) return@withContext emptyList()

        val quizList = mutableListOf<QuizItem>()
        var quizId = 1

        for ((idx, chunk) in chunks.take(6).withIndex()) {
            val sentences = chunk.text.split(Regex("[.!?\\n]\\s*")).filter { it.trim().length > 30 }
            if (sentences.isNotEmpty()) {
                val statement = sentences.first().trim()
                val words = statement.split(" ")
                val keyword = words.getOrNull(words.size / 2) ?: "key concept"

                quizList.add(
                    QuizItem(
                        id = quizId++,
                        question = "Which statement accurately reflects Section ${chunk.ordinal + 1} regarding $keyword?",
                        options = listOf(
                            statement,
                            "It contradicts the fundamental properties established in earlier theorems.",
                            "It requires external network synchronization to validate state transitions.",
                            "It is deprecated in favor of manual non-deterministic processes."
                        ).shuffled(),
                        correctIndex = 0, // Will match the true statement
                        explanation = "According to Section ${chunk.ordinal + 1}: \"$statement\""
                    )
                )
            }
        }

        // Adjust correctIndex after shuffle
        val finalized = quizList.map { item ->
            val correctOpt = item.explanation.substringAfter(": \"").substringBefore("\"")
            val index = item.options.indexOfFirst { it == correctOpt }.coerceAtLeast(0)
            item.copy(correctIndex = index)
        }

        finalized
    }

    data class RagAnswer(val answer: String, val sources: List<RetrievedChunk>)
}
