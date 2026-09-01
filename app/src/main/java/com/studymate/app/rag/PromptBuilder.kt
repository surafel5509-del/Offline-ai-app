package com.studymate.app.rag

/**
 * Builds structured prompts for the local LLM.
 * Strictly in pure English as requested, with support for custom system instructions.
 */
object PromptBuilder {

    /** Default fallback system instruction */
    const val DEFAULT_SYSTEM_INSTRUCTION = """You are StudyMate, an expert academic tutor and AI study assistant running 100% offline on-device.
Rules:
- Be accurate, clear, and structured.
- When answering from context, use only the provided document text.
- If unsure or if information is missing from the document, state it clearly rather than guessing.
- Format responses cleanly with bold headings and bullet points."""

    /**
     * RAG prompt: combines system instruction, retrieved chunks, and the user's question.
     */
    fun ragPrompt(
        question: String,
        contextChunks: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION
    ): String {
        val context = contextChunks.joinToString("\n---\n") { it.trim() }
        return """$systemInstruction

Use the following document excerpts to answer the question thoroughly and accurately:

Context:
$context

Question: $question
Answer:"""
    }

    /**
     * Plain chat prompt with system instruction prepended.
     */
    fun chatPrompt(
        question: String,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION
    ): String = "$systemInstruction\n\nQuestion: $question\nAnswer:"

    /**
     * Document summary prompt.
     */
    fun summaryPrompt(
        documentTitle: String,
        sampleText: String,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION
    ): String = """$systemInstruction

Please provide a structured, executive study summary of the document "$documentTitle" based on the following text:

Content:
$sampleText

Generate:
1. Executive Overview
2. Key Insights & Takeaways (Bullet points)
3. Essential Definitions
4. Suggested Review Topics"""

    /**
     * Flashcard generation prompt.
     */
    fun flashcardPrompt(sampleText: String): String = """Extract 5 high-yield study flashcards from the text below in JSON format:
[{"front": "Question/Term", "back": "Answer/Definition"}]

Text:
$sampleText"""

    /**
     * Quiz generation prompt.
     */
    fun quizPrompt(sampleText: String): String = """Generate 3 multiple-choice study quiz questions based on the following content:

Text:
$sampleText"""
}
