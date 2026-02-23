package it.aw.documentingest.controller;

import it.aw.documentingest.model.ChunkingParams;
import it.aw.documentingest.model.DocumentRecord;
import it.aw.documentingest.model.DocumentSummary;
import it.aw.documentingest.model.SearchResult;
import it.aw.documentingest.model.StoreStats;
import it.aw.documentingest.registry.DocumentRegistry;
import it.aw.documentingest.service.IngestionService;
import it.aw.documentingest.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Espone le operazioni CRUD, di ricerca e di info sui documenti indicizzati.
 *
 * Endpoint disponibili:
 *   POST   /api/documents/ingest          — indicizza un nuovo documento
 *   GET    /api/documents/search?q=&limit= — ricerca semantica
 *   GET    /api/documents                 — lista tutti i documenti indicizzati
 *   GET    /api/documents/stats           — statistiche aggregate dello store
 *   GET    /api/documents/{filename}      — dettaglio e chunk preview di un documento
 *   DELETE /api/documents/{filename}      — rimuove un documento dall'indice
 *   PUT    /api/documents/{filename}      — sostituisce un documento con una nuova versione
 *
 * Nota: i path letterali /search e /stats hanno priorità su /{filename} in Spring MVC,
 * quindi non si crea ambiguità.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final IngestionService ingestionService;
    private final SearchService searchService;
    private final DocumentRegistry registry;

    public DocumentController(IngestionService ingestionService,
                              SearchService searchService,
                              DocumentRegistry registry) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
        this.registry = registry;
    }

    // -------------------------------------------------------------------------
    // POST /api/documents/ingest
    // -------------------------------------------------------------------------

    /**
     * Indicizza un documento (PDF o testo).
     * I parametri chunkSize e overlap sono opzionali: se omessi si usano i default (500/50).
     *
     * Esempio con default:
     *   curl -X POST http://localhost:8889/api/documents/ingest \
     *        -F "file=@documento.pdf"
     *
     * Esempio con chunking personalizzato:
     *   curl -X POST "http://localhost:8889/api/documents/ingest?chunkSize=200&overlap=20" \
     *        -F "file=@faq.txt"
     */
    @PostMapping("/ingest")
    public ResponseEntity<DocumentSummary> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkSize", defaultValue = "" + ChunkingParams.DEFAULT_CHUNK_SIZE) int chunkSize,
            @RequestParam(value = "overlap",   defaultValue = "" + ChunkingParams.DEFAULT_OVERLAP)    int overlap) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ChunkingParams params;
        try {
            params = new ChunkingParams(chunkSize, overlap);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        try {
            DocumentSummary summary = ingestionService.ingest(file, params);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Errore durante l'ingestione: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/documents/search?q=...&limit=5
    // -------------------------------------------------------------------------

    /**
     * Ricerca semantica sui documenti indicizzati.
     *
     * Esempio:
     *   curl "http://localhost:8889/api/documents/search?q=fattura+2024&limit=3"
     */
    @GetMapping("/search")
    public ResponseEntity<List<SearchResult>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        if (query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResult> results = searchService.search(query, limit);
        return ResponseEntity.ok(results);
    }

    // -------------------------------------------------------------------------
    // GET /api/documents
    // -------------------------------------------------------------------------

    /**
     * Restituisce tutti i documenti attualmente indicizzati con i rispettivi metadati
     * e le anteprime dei chunk.
     *
     * Esempio:
     *   curl http://localhost:8889/api/documents
     */
    @GetMapping
    public ResponseEntity<List<DocumentSummary>> listDocuments() {
        return ResponseEntity.ok(registry.findAllAsSummary());
    }

    // -------------------------------------------------------------------------
    // GET /api/documents/stats
    // -------------------------------------------------------------------------

    /**
     * Statistiche aggregate: numero documenti, chunk totali, tipo di store.
     *
     * Esempio:
     *   curl http://localhost:8889/api/documents/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<StoreStats> stats() {
        StoreStats stats = new StoreStats(
                registry.totalDocuments(),
                registry.totalChunks(),
                "DuckDB",
                "AllMiniLmL6V2Quantized",
                false
        );
        return ResponseEntity.ok(stats);
    }

    // -------------------------------------------------------------------------
    // GET /api/documents/{filename}
    // -------------------------------------------------------------------------

    /**
     * Dettaglio di un singolo documento: metadati e anteprima dei chunk.
     *
     * Esempio:
     *   curl "http://localhost:8889/api/documents/report.pdf"
     */
    @GetMapping("/{filename}")
    public ResponseEntity<DocumentRecord> getDocument(@PathVariable String filename) {
        return registry.findByFilename(filename)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/documents/{filename}
    // -------------------------------------------------------------------------

    /**
     * Rimuove un documento dall'indice. I chunk fisici restano nello store in-memory
     * ma vengono filtrati automaticamente da tutte le ricerche successive.
     *
     * Esempio:
     *   curl -X DELETE http://localhost:8889/api/documents/report.pdf
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String filename) {
        boolean removed = registry.remove(filename);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // -------------------------------------------------------------------------
    // PUT /api/documents/{filename}
    // -------------------------------------------------------------------------

    /**
     * Sostituisce un documento esistente con una nuova versione del file.
     * Il vecchio contenuto viene rimosso dall'indice attivo; i nuovi chunk
     * vengono indicizzati con un nuovo documentId.
     * I parametri chunkSize e overlap sono opzionali: se omessi si usano i default (500/50).
     *
     * Esempio:
     *   curl -X PUT "http://localhost:8889/api/documents/report.pdf?chunkSize=300&overlap=30" \
     *        -F "file=@report_v2.pdf"
     */
    @PutMapping("/{filename}")
    public ResponseEntity<DocumentSummary> reingestDocument(
            @PathVariable String filename,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkSize", defaultValue = "" + ChunkingParams.DEFAULT_CHUNK_SIZE) int chunkSize,
            @RequestParam(value = "overlap",   defaultValue = "" + ChunkingParams.DEFAULT_OVERLAP)    int overlap) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (registry.findByFilename(filename).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ChunkingParams params;
        try {
            params = new ChunkingParams(chunkSize, overlap);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        try {
            DocumentSummary summary = ingestionService.reingest(filename, file, params);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Errore durante il re-ingest: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
