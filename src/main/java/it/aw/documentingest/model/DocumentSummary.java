package it.aw.documentingest.model;

import java.time.LocalDateTime;

/**
 * Vista leggera di un documento indicizzato: metadati e contatori, senza dettaglio chunk.
 * <p>
 * Restituita da POST /ingest, PUT /{documentId} e GET /api/documents (lista).
 * Per il dettaglio completo con preview dei chunk usare GET /api/documents/{documentId}.
 */
public record DocumentSummary(
        String        projectId,
        String        documentId,
        String        filename,
        LocalDateTime ingestedAt,
        int           chunkCount,
        int           chunkSize,
        int           overlap,
        int           sectionCount  // sezioni distinte rilevate (0 = documento piatto)
) {}
