package it.aw.documentingest.model;

import java.time.LocalDateTime;

/**
 * Vista leggera di un documento indicizzato: metadati e contatori, senza dettaglio chunk.
 * <p>
 * Restituita da POST /ingest, PUT /{filename} e GET /api/documents (lista).
 * Per il dettaglio completo con preview dei chunk usare GET /api/documents/{filename}.
 */
public record DocumentSummary(
        String        filename,
        String        documentId,
        LocalDateTime ingestedAt,
        int           chunkCount,
        int           chunkSize,
        int           overlap,
        int           sectionCount  // sezioni distinte rilevate (0 = documento piatto)
) {}
