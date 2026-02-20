package it.aw.documentingest.model;

/**
 * Parametri di chunking per una singola operazione di ingestione.
 * <p>
 * Ogni documento può essere indicizzato con valori diversi; tutti i chunk
 * confluiscono nello stesso embedding store e sono interrogabili in modo uniforme,
 * poiché il modello AllMiniLM-L6-v2 mappa qualsiasi testo nello stesso
 * spazio vettoriale a 384 dimensioni indipendentemente dalla dimensione del chunk.
 */
public record ChunkingParams(int chunkSize, int overlap) {

    public static final int DEFAULT_CHUNK_SIZE = 500;
    public static final int DEFAULT_OVERLAP    = 50;

    /** Costruttore compatto con validazione. */
    public ChunkingParams {
        if (chunkSize < 50) {
            throw new IllegalArgumentException("chunkSize deve essere >= 50 (ricevuto: " + chunkSize + ")");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap deve essere >= 0 (ricevuto: " + overlap + ")");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "overlap (" + overlap + ") deve essere < chunkSize (" + chunkSize + ")");
        }
    }

    public static ChunkingParams defaults() {
        return new ChunkingParams(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }
}
