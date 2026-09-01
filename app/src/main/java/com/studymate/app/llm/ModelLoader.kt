package com.studymate.app.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.studymate.app.util.IoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Detailed model file representation for the settings and model inspector.
 */
data class ModelFileInfo(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val formatTag: String,
    val isBundledAsset: Boolean,
    val isRecommended: Boolean,
    val isSelected: Boolean
)

/**
 * Model format recognition and management. Supports .tflite, .task, .bin, .gguf, .onnx, .pt.
 */
object ModelLoader {

    private const val TAG = "StudyMate/ModelLoader"

    /** Supported file extensions. */
    val SUPPORTED_EXTENSIONS = listOf(
        ".tflite",
        ".task",
        ".bin",
        ".gguf",
        ".onnx",
        ".pt",
        ".engine",
        ".weights"
    )

    /** Known candidate model file names in order of priority. */
    val CANDIDATES = listOf(
        "TinyLlama-1.1B-Chat-v1.0.Q4_K_M.tflite",
        "TinyLlama-1.1B-Chat-v1.0.Q4_K_M.bin",
        "tinyllama-1.1b-chat.tflite",
        "tinyllama-1.1b.tflite",
        "tinyllama.tflite",
        "qwen2.5-1.5b-instruct-q4.tflite",
        "qwen2.5-1.5b.tflite",
        "gemma-2b-it-gpu-int4.tflite",
        "gemma-2b-it-cpu-int4.tflite",
        "gemma-2b.tflite",
        "model.tflite",
        "model.task",
        "model.bin",
        "model.gguf"
    )

    /**
     * Resolves the active model path based on user preference or automatic discovery.
     */
    fun resolveModelPath(context: Context, preferredModelName: String? = null): String? {
        val modelsDir = IoUtils.ensureModelsDir(context)

        // 1. If user explicitly selected a model and it exists on disk
        if (!preferredModelName.isNullOrBlank()) {
            val userFile = File(modelsDir, preferredModelName)
            if (userFile.exists() && userFile.length() > 0) {
                return userFile.absolutePath
            }
        }

        // 2. Check prioritized candidates in filesDir/models/
        for (candidate in CANDIDATES) {
            val file = File(modelsDir, candidate)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
        }

        // 3. Check ANY supported model file in filesDir/models/
        val anyModel = modelsDir.listFiles { _, name ->
            SUPPORTED_EXTENSIONS.any { ext -> name.endsWith(ext, ignoreCase = true) }
        }?.maxByOrNull { it.length() }

        if (anyModel != null && anyModel.length() > 0) {
            return anyModel.absolutePath
        }

        // 4. Check assets for bundled models
        for (candidate in CANDIDATES) {
            if (assetExists(context, candidate)) {
                val outFile = File(modelsDir, candidate)
                if (!outFile.exists() || outFile.length() == 0L) {
                    try {
                        context.assets.open(candidate).use { input ->
                            FileOutputStream(outFile).use { output -> input.copyTo(output) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed copying asset model: $candidate", e)
                        continue
                    }
                }
                return outFile.absolutePath
            }
        }

        return null
    }

    /**
     * Returns a list of all model files discovered on the device.
     */
    fun getAvailableModels(context: Context, selectedName: String? = null): List<ModelFileInfo> {
        val list = mutableListOf<ModelFileInfo>()
        val modelsDir = IoUtils.ensureModelsDir(context)
        val activePath = resolveModelPath(context, selectedName)

        modelsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.length() > 0 &&
                SUPPORTED_EXTENSIONS.any { ext -> file.name.endsWith(ext, ignoreCase = true) }
            ) {
                val isSelected = file.absolutePath == activePath
                list.add(
                    ModelFileInfo(
                        fileName = file.name,
                        absolutePath = file.absolutePath,
                        sizeBytes = file.length(),
                        formattedSize = formatBytes(file.length()),
                        formatTag = determineFormatTag(file.name),
                        isBundledAsset = false,
                        isRecommended = file.name.contains("TinyLlama", ignoreCase = true) || file.name.contains("Qwen", ignoreCase = true),
                        isSelected = isSelected
                    )
                )
            }
        }

        return list.sortedWith(
            compareByDescending<ModelFileInfo> { it.isSelected }
                .thenByDescending { it.isRecommended }
                .thenByDescending { it.sizeBytes }
        )
    }

    /**
     * Import a model file from SAF URI into `filesDir/models/`.
     */
    suspend fun importModelFromUri(
        context: Context,
        uri: Uri,
        fileName: String,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val modelsDir = IoUtils.ensureModelsDir(context)
        val cleanName = if (fileName.isBlank()) "custom_model.tflite" else fileName.replace(" ", "_")
        val destFile = File(modelsDir, cleanName)

        val totalBytes = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
        } catch (_: Exception) {
            -1L
        }

        context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(128 * 1024)
                var bytesCopied = 0L
                var read: Int
                while (input.read(buffer).also { read = it } >= 0) {
                    output.write(buffer, 0, read)
                    bytesCopied += read
                    onProgress(bytesCopied, totalBytes)
                }
                output.flush()
            }
        } ?: throw IllegalStateException("Could not access the selected file stream.")

        destFile.absolutePath
    }

    /**
     * Deletes a model from local storage.
     */
    fun deleteModel(context: Context, fileName: String): Boolean {
        val modelsDir = IoUtils.ensureModelsDir(context)
        val file = File(modelsDir, fileName)
        return if (file.exists()) file.delete() else false
    }

    /**
     * Total storage occupied by model files.
     */
    fun getTotalModelsSizeBytes(context: Context): Long {
        val modelsDir = IoUtils.ensureModelsDir(context)
        return modelsDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    private fun determineFormatTag(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".tflite") -> "TFLite Graph"
            lower.endsWith(".task") -> "MediaPipe Task"
            lower.endsWith(".bin") -> "Binary Weights"
            lower.endsWith(".gguf") -> "GGUF Quantized"
            lower.endsWith(".onnx") -> "ONNX Engine"
            lower.endsWith(".pt") -> "PyTorch TorchScript"
            else -> "Offline Weights"
        }
    }

    private fun assetExists(context: Context, name: String): Boolean {
        return context.assets.list("")?.contains(name) == true
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
