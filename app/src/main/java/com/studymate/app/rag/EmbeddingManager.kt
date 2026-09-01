package com.studymate.app.rag

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import com.studymate.app.util.IoUtils
import com.studymate.app.util.VectorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * On-device text embedding model wrapper.
 *
 * Primary engine: MediaPipe's [TextEmbedder] with Universal Sentence Encoder (USE) `.tflite`.
 * Fallback engine: Built-in deterministic L2-normalized subword/character n-gram semantic
 * vectorizer that works 100% offline with zero dependencies and handles both Amharic & English.
 *
 * Vectors are always L2-normalized on output so cosine similarity reduces to a fast dot product.
 */
class EmbeddingManager(private val context: Context) {

    @Volatile
    private var embedder: TextEmbedder? = null
    @Volatile
    private var isUsingFallback = false

    /** Lazy-load the embedder when needed. */
    private fun ensureLoaded() {
        if (embedder != null || isUsingFallback) return
        synchronized(this) {
            if (embedder != null || isUsingFallback) return
            val modelPath = resolveModelAsset()
            if (modelPath != null && File(modelPath).exists()) {
                try {
                    val baseOptions = BaseOptions.builder()
                        .setModelAssetPath(modelPath)
                        .build()
                    val options = TextEmbedderOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setL2Normalize(true)
                        .build()
                    embedder = TextEmbedder.createFromOptions(context, options)
                    Log.i(TAG, "MediaPipe TextEmbedder loaded successfully from $modelPath")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "MediaPipe TextEmbedder failed to initialize, using on-device fallback vectorizer", e)
                }
            } else {
                Log.i(TAG, "No embedding model asset found on disk, using built-in high-speed semantic vectorizer")
            }
            isUsingFallback = true
        }
    }

    /**
     * Look for the embedding model in filesDir or assets.
     */
    private fun resolveModelAsset(): String? {
        val assetName = EMBEDDING_MODEL_NAME
        val outDir = IoUtils.ensureModelsDir(context)
        val outFile = File(outDir, assetName)
        if (outFile.exists() && outFile.length() > 0) {
            return outFile.absolutePath
        }
        return try {
            if (context.assets.list("")?.contains(assetName) == true) {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                outFile.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Embed text into a dense L2-normalized float vector.
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        ensureLoaded()
        val mediaPipeEmbedder = embedder
        if (mediaPipeEmbedder != null && !isUsingFallback) {
            try {
                val result = mediaPipeEmbedder.embed(text).embeddingResult()
                val floatVec = result.embeddings().firstOrNull()?.floatEmbedding()
                if (floatVec != null && floatVec.isNotEmpty()) {
                    return@withContext floatVec
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe embed() call failed, falling back to internal vectorizer", e)
            }
        }
        // Fallback: high-speed deterministic semantic vectorizer (128 dimensions)
        deterministicEmbed(text)
    }

    /**
     * Generates a 128-dimensional dense semantic embedding vector by hashing word tokens,
     * subwords, and character 3-grams with term frequency scaling and L2 normalization.
     * Works with Latin, Ethiopic (Amharic), and punctuation.
     */
    private fun deterministicEmbed(text: String, dimensions: Int = 128): FloatArray {
        val vector = FloatArray(dimensions)
        val normalized = text.lowercase().trim()
        val tokens = normalized.split(Regex("[\\s,;:.!?()\"'«»።፣፤፦]+")).filter { it.length >= 2 }

        if (tokens.isEmpty()) {
            // Hash the raw string
            val idx = (normalized.hashCode() and 0x7fffffff) % dimensions
            vector[idx] = 1.0f
            return vector
        }

        // 1. Word token hashing
        for (token in tokens) {
            val h1 = (token.hashCode() and 0x7fffffff) % dimensions
            val h2 = ((token.hashCode() * 31 + 17) and 0x7fffffff) % dimensions
            vector[h1] += 1.5f
            vector[h2] += 0.8f

            // 2. Character 3-grams for morphological similarity
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val gram = token.substring(i, i + 3)
                    val gh = (gram.hashCode() and 0x7fffffff) % dimensions
                    vector[gh] += 0.5f
                }
            }
        }

        // 3. L2 Normalize vector
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 0.0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    fun isNativeModelLoaded(): Boolean = embedder != null && !isUsingFallback

    fun close() {
        synchronized(this) {
            try {
                embedder?.close()
            } catch (_: Exception) {}
            embedder = null
            isUsingFallback = false
        }
    }

    companion object {
        private const val TAG = "StudyMate/Embedder"
        const val EMBEDDING_MODEL_NAME = "universal_sentence_encoder_quantized.tflite"
    }
}
