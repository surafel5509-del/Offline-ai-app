package com.studymate.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistent user preferences, appearance, and AI hyperparameters.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("studymate_settings", Context.MODE_PRIVATE)

    // Theme Mode: "system", "dark", "light"
    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Model Parameters
    private val _temperature = MutableStateFlow(prefs.getFloat(KEY_TEMPERATURE, 0.7f))
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _maxTokens = MutableStateFlow(prefs.getInt(KEY_MAX_TOKENS, 1024))
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _topK = MutableStateFlow(prefs.getInt(KEY_TOP_K, 40))
    val topK: StateFlow<Int> = _topK.asStateFlow()

    private val _systemInstruction = MutableStateFlow(
        prefs.getString(KEY_SYSTEM_INSTRUCTION, DEFAULT_SYSTEM_INSTRUCTION) ?: DEFAULT_SYSTEM_INSTRUCTION
    )
    val systemInstruction: StateFlow<String> = _systemInstruction.asStateFlow()

    // Selected model file name or path (null = auto-detect)
    private val _selectedModelName = MutableStateFlow(prefs.getString(KEY_SELECTED_MODEL, null))
    val selectedModelName: StateFlow<String?> = _selectedModelName.asStateFlow()

    // Similarity threshold for RAG retrieval (0.2 to 0.9)
    private val _similarityThreshold = MutableStateFlow(prefs.getFloat(KEY_SIMILARITY_THRESHOLD, 0.35f))
    val similarityThreshold: StateFlow<Float> = _similarityThreshold.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun setTemperature(temp: Float) {
        val clamped = temp.coerceIn(0.1f, 1.5f)
        prefs.edit().putFloat(KEY_TEMPERATURE, clamped).apply()
        _temperature.value = clamped
    }

    fun setMaxTokens(tokens: Int) {
        val clamped = tokens.coerceIn(128, 2048)
        prefs.edit().putInt(KEY_MAX_TOKENS, clamped).apply()
        _maxTokens.value = clamped
    }

    fun setTopK(k: Int) {
        val clamped = k.coerceIn(1, 100)
        prefs.edit().putInt(KEY_TOP_K, clamped).apply()
        _topK.value = clamped
    }

    fun setSystemInstruction(instruction: String) {
        val cleaned = instruction.trim()
        prefs.edit().putString(KEY_SYSTEM_INSTRUCTION, cleaned).apply()
        _systemInstruction.value = cleaned
    }

    fun setSelectedModel(fileName: String?) {
        prefs.edit().putString(KEY_SELECTED_MODEL, fileName).apply()
        _selectedModelName.value = fileName
    }

    fun setSimilarityThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        prefs.edit().putFloat(KEY_SIMILARITY_THRESHOLD, clamped).apply()
        _similarityThreshold.value = clamped
    }

    fun resetToDefaults() {
        setThemeMode(THEME_SYSTEM)
        setTemperature(0.7f)
        setMaxTokens(1024)
        setTopK(40)
        setSystemInstruction(DEFAULT_SYSTEM_INSTRUCTION)
        setSelectedModel(null)
        setSimilarityThreshold(0.35f)
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"

        const val DEFAULT_SYSTEM_INSTRUCTION =
            "You are StudyMate, an expert academic tutor and AI study assistant running 100% offline on-device. " +
            "Provide insightful, well-structured, and accurate explanations with clear bullet points and examples."

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TOP_K = "top_k"
        private const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SIMILARITY_THRESHOLD = "similarity_threshold"
    }
}
