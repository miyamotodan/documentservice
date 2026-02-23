package it.aw.documentingest.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dettaglio completo di un documento indicizzato, incluse le preview dei chunk
 * con metadati di sezione e pagina.
 * <p>
 * Restituito solo da GET /api/documents/{filename}.
 * Per le operazioni di lista e ingest usare {@link DocumentSummary}.
 */
public record DocumentRecord(
        String           filename,
        String           documentId,
        LocalDateTime    ingestedAt,
        int              chunkCount,
        int              chunkSize,
        int              overlap,
        int              sectionCount,      // sezioni distinte rilevate (0 = documento piatto)
        List<ChunkInfo>  chunkPreviews      // dettaglio chunk con sezione e pagina
) {
    /** Proietta il record nella vista leggera senza chunk preview. */
    public DocumentSummary toSummary() {
        return new DocumentSummary(filename, documentId, ingestedAt,
                chunkCount, chunkSize, overlap, sectionCount);
    }
}
