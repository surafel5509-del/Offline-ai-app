package com.studymate.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.studymate.app.data.DocumentRepository
import com.studymate.app.data.SettingsManager
import com.studymate.app.llm.LlmManager
import com.studymate.app.rag.EmbeddingManager
import com.studymate.app.rag.RagService
import com.studymate.app.rag.TextExtractor

/**
 * Application entry point. Lazy loads heavy ML native models and maintains global dependencies.
 */
class StudyMateApp : Application() {

    val settingsManager: SettingsManager by lazy { SettingsManager(this) }
    val repository: DocumentRepository by lazy { DocumentRepository(this) }
    val llmManager: LlmManager by lazy { LlmManager(this, settingsManager).also { llmLoaded = true } }
    val embeddingManager: EmbeddingManager by lazy { EmbeddingManager(this).also { embLoaded = true } }
    val ragService: RagService by lazy {
        RagService(repository, TextExtractor(this), embeddingManager, llmManager)
    }

    @Volatile private var llmLoaded = false
    @Volatile private var embLoaded = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    try {
                        if (llmLoaded) {
                            Log.i(TAG, "Process backgrounded — unloading LLM + embedding models")
                            llmManager.unload()
                            embeddingManager.close()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error unloading models on background", e)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register lifecycle observer", e)
        }
    }

    companion object {
        private const val TAG = "StudyMateApp"
        @Volatile
        lateinit var instance: StudyMateApp
            private set
    }
}
