package it.aw.documentingest.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rappresentazione di un documento indicizzato nel registry.
 * Utilizzata sia internamente che come risposta JSON.
 * <p>
 * I campi chunkSize e overlap registrano i parametri di chunking
 * usati durante l'ingestione, utili per capire la granularità dell'indice.
 * Documenti con parametri diversi coesistono nello stesso store
 * e sono interrogabili in modo uniforme.
 */
public record DocumentRecord(
        String filename,
        String documentId,
        LocalDateTime ingestedAt,
        int chunkCount,
        int chunkSize,
        int overlap,
        List<String> chunkPreviews
) {}
