package it.aw.documentingest.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import it.aw.documentingest.model.ChunkingParams;
import it.aw.documentingest.model.DocumentRecord;
import it.aw.documentingest.registry.DocumentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestisce il ciclo di vita dei documenti: ingestione e re-ingestione.
 * <p>
 * I parametri di chunking (chunkSize, overlap) sono specificati per chiamata:
 * ogni documento può essere indicizzato con granularità diversa. Tutti i chunk
 * confluiscono nello stesso embedding store e sono interrogabili in modo uniforme,
 * poiché il modello vettoriale produce embedding nello stesso spazio a 384 dimensioni
 * indipendentemente dalla dimensione del testo.
 * <p>
 * Strategia per delete/re-ingest: ogni ingestione genera un documentId (UUID) scritto
 * nei metadati di ogni chunk. SearchService filtra i risultati mantenendo solo i chunk
 * il cui documentId è ancora attivo nel registry. I chunk "orfani" restano fisicamente
 * nello store ma non vengono mai restituiti.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int PREVIEW_LENGTH = 150;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentRegistry registry;

    public IngestionService(EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            DocumentRegistry registry) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.registry = registry;
    }

    /**
     * Indicizza un documento con i parametri di chunking forniti.
     * Usare {@link ChunkingParams#defaults()} se non specificati dal chiamante.
     */
    public DocumentRecord ingest(MultipartFile file, ChunkingParams params) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        return doIngest(filename, file, params);
    }

    /**
     * Sostituisce un documento esistente con una nuova versione.
     * Il vecchio documentId viene rimosso dal registry; i chunk orfani restano
     * nello store ma saranno invisibili alle ricerche.
     */
    public DocumentRecord reingest(String canonicalFilename, MultipartFile file, ChunkingParams params)
            throws IOException {
        registry.remove(canonicalFilename);
        return doIngest(canonicalFilename, file, params);
    }

    private DocumentRecord doIngest(String filename, MultipartFile file, ChunkingParams params)
            throws IOException {
        String documentId = UUID.randomUUID().toString();
        log.info("Inizio ingestione: {} — chunkSize={}, overlap={}, documentId={}",
                filename, params.chunkSize(), params.overlap(), documentId);

        DocumentSplitter splitter = DocumentSplitters.recursive(params.chunkSize(), params.overlap());
        Document document = parse(file, filename, documentId);
        List<TextSegment> segments = splitter.split(document);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        List<String> previews = segments.stream()
                .map(s -> s.text().length() > PREVIEW_LENGTH
                        ? s.text().substring(0, PREVIEW_LENGTH) + "..."
                        : s.text())
                .collect(Collectors.toList());

        DocumentRecord record = new DocumentRecord(
                filename, documentId, LocalDateTime.now(),
                segments.size(), params.chunkSize(), params.overlap(),
                previews);
        registry.register(record);

        log.info("Ingestione completata: {} — {} chunk (documentId={})", filename, segments.size(), documentId);
        return record;
    }

    private Document parse(MultipartFile file, String filename, String documentId) throws IOException {
        Metadata metadata = new Metadata();
        metadata.put("filename", filename);
        metadata.put("documentId", documentId);

        String contentType = file.getContentType();
        if ("application/pdf".equals(contentType) || filename.toLowerCase().endsWith(".pdf")) {
            ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
            Document doc;
            try (var is = file.getInputStream()) {
                doc = parser.parse(is);
            }
            doc.metadata().put("filename", filename);
            doc.metadata().put("documentId", documentId);
            return doc;
        }

        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return Document.from(text, metadata);
    }
}
