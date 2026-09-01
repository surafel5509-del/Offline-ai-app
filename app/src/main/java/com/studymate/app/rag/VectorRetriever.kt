package com.studymate.app.rag

import com.studymate.app.util.VectorMath

/**
 * In-memory cosine retriever over a document's chunks.
 *
 * Loads all chunks for a document once (text + embeddings), then answers each query
 * with a single linear scan. For a typical study document (≤ ~5k chunks) this is a few
 * milliseconds and uses only the embedding arrays in RAM — far cheaper than a native
 * vector index library, and fully offline.
 */
class VectorRetriever(private val chunks: List<RetrievedChunk>) {

    /**
     * Return the top [k] most similar chunks to [queryEmbedding] meeting [minScore].
     * If [chunks] is empty, returns an empty list.
     */
    fun retrieve(queryEmbedding: FloatArray, k: Int = 4, minScore: Float = 0.0f): List<RetrievedChunk> {
        if (chunks.isEmpty()) return emptyList()
        val scored = chunks
            .map { it.copy(score = VectorMath.cosineSimilarity(queryEmbedding, it.embedding)) }
            .sortedByDescending { it.score }

        val filtered = scored.filter { it.score >= minScore }
        return (if (filtered.isNotEmpty()) filtered else scored).take(k)
    }
}
