package it.aw.documentingest.model;

/**
 * Statistiche aggregate sullo stato dell'embedding store.
 */
public record StoreStats(
        int totalDocuments,
        int totalChunks,
        String storeType,
        String embeddingModel,
        boolean ephemeral
) {}
