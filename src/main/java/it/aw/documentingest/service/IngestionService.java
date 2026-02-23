package it.aw.documentingest.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import it.aw.documentingest.model.ChunkInfo;
import it.aw.documentingest.model.ChunkingParams;
import it.aw.documentingest.model.DocumentRecord;
import it.aw.documentingest.model.DocumentSummary;
import it.aw.documentingest.registry.DocumentRegistry;
import it.aw.documentingest.service.PdfPageParser.PagedText;
import it.aw.documentingest.service.SectionDetector.SectionBoundary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Gestisce il ciclo di vita dei documenti: ingestione e re-ingestione.
 * <p>
 * Pipeline:
 * <ol>
 *   <li>Parse: PDF pagina per pagina via PdfPageParser; TXT testo grezzo</li>
 *   <li>Section detection: SectionDetector rileva heading con pattern espliciti</li>
 *   <li>Chunking: DocumentSplitters.recursive sul testo completo</li>
 *   <li>Metadata enrichment: per ogni chunk calcola sezione e page range</li>
 *   <li>Embedding + store</li>
 *   <li>Registra il DocumentRecord nel registry DuckDB</li>
 * </ol>
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

    /** Indicizza un nuovo documento. */
    public DocumentSummary ingest(MultipartFile file, ChunkingParams params) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        return doIngest(filename, file, params);
    }

    /**
     * Sostituisce un documento esistente con una nuova versione.
     * Il vecchio documentId viene rimosso dal registry; i chunk orfani restano
     * nello store ma saranno invisibili alle ricerche.
     */
    public DocumentSummary reingest(String canonicalFilename, MultipartFile file, ChunkingParams params)
            throws IOException {
        registry.remove(canonicalFilename);
        return doIngest(canonicalFilename, file, params);
    }

    private DocumentSummary doIngest(String filename, MultipartFile file, ChunkingParams params)
            throws IOException {
        String documentId = UUID.randomUUID().toString();
        log.info("Inizio ingestione: {} — chunkSize={}, overlap={}, documentId={}",
                filename, params.chunkSize(), params.overlap(), documentId);

        // [1] Parse + info pagina
        boolean isPdf = isPdf(file, filename);
        String fullText;
        PagedText pagedText = null;
        if (isPdf) {
            try (var is = file.getInputStream()) {
                pagedText = PdfPageParser.parse(is);
            }
            fullText = pagedText.fullText();
        } else {
            fullText = new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        // [2] Section detection
        List<SectionBoundary> boundaries = SectionDetector.detect(fullText);
        int sectionCount = (int) boundaries.stream()
                .filter(b -> b.level() == 1).count();
        log.debug("Section detection: {} heading rilevati, {} sezioni L1", boundaries.size(), sectionCount);

        // [3] Chunking sul testo completo
        DocumentSplitter splitter = DocumentSplitters.recursive(params.chunkSize(), params.overlap());
        Metadata baseMetadata = new Metadata();
        baseMetadata.put("filename", filename);
        baseMetadata.put("documentId", documentId);
        Document document = Document.from(fullText, baseMetadata);
        List<TextSegment> segments = splitter.split(document);

        // [4] Metadata enrichment: sezione + pagina per ogni chunk
        List<ChunkInfo> previews = enrichSegments(segments, fullText, boundaries, pagedText);

        // [5] Embedding + store
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        // [6] Register
        DocumentRecord record = new DocumentRecord(
                filename, documentId, LocalDateTime.now(),
                segments.size(), params.chunkSize(), params.overlap(),
                sectionCount, previews);
        registry.register(record);

        log.info("Ingestione completata: {} — {} chunk, {} sezioni L1 (documentId={})",
                filename, segments.size(), sectionCount, documentId);
        return record.toSummary();
    }

    private List<ChunkInfo> enrichSegments(
            List<TextSegment> segments,
            String fullText,
            List<SectionBoundary> boundaries,
            PagedText pagedText) {

        List<ChunkInfo> previews = new ArrayList<>(segments.size());
        Map<String, Integer> sectionChunkCounter = new HashMap<>();
        int searchFrom = 0;

        for (TextSegment segment : segments) {
            String chunkText = segment.text();

            // Stima offset del chunk nel fullText con ricerca progressiva
            int chunkOffset = fullText.indexOf(chunkText, searchFrom);
            if (chunkOffset < 0) chunkOffset = fullText.indexOf(chunkText);
            if (chunkOffset >= 0) searchFrom = chunkOffset + 1;

            int effectiveOffset = Math.max(chunkOffset, 0);

            // Gerarchia sezioni all'offset del chunk
            String[] h = SectionDetector.hierarchyAt(effectiveOffset, boundaries);
            String l1 = h[0], l2 = h[1], l3 = h[2];

            String sectionPath  = buildPath(l1, l2, l3);
            int    sectionLevel = (l3 != null) ? 3 : (l2 != null) ? 2 : (l1 != null) ? 1 : 0;
            String sectionTitle = (l3 != null) ? l3 : (l2 != null) ? l2 : (l1 != null) ? l1 : "";

            int chunkIndex = sectionChunkCounter.getOrDefault(sectionPath, 0);
            sectionChunkCounter.put(sectionPath, chunkIndex + 1);

            // Page range (solo PDF)
            Integer pageStart = null, pageEnd = null;
            if (pagedText != null && chunkOffset >= 0) {
                int[] range = pagedText.pageRangeFor(chunkOffset, chunkOffset + chunkText.length());
                if (range != null) { pageStart = range[0]; pageEnd = range[1]; }
            }

            // Scrivi metadati nel TextSegment (per l'embedding store)
            Metadata meta = segment.metadata();
            meta.put("section.path",  sectionPath);
            meta.put("section.title", sectionTitle);
            meta.put("section.level", sectionLevel);
            meta.put("chunk.index",   chunkIndex);
            if (l1 != null) meta.put("section.l1", l1);
            if (l2 != null) meta.put("section.l2", l2);
            if (l3 != null) meta.put("section.l3", l3);
            if (pageStart != null) meta.put("chunk.page_start", pageStart);
            if (pageEnd   != null) meta.put("chunk.page_end",   pageEnd);

            String preview = chunkText.length() > PREVIEW_LENGTH
                    ? chunkText.substring(0, PREVIEW_LENGTH) + "..."
                    : chunkText;
            previews.add(new ChunkInfo(chunkIndex, l1, l2, l3,
                    sectionTitle, sectionPath, sectionLevel,
                    pageStart, pageEnd, preview));
        }

        return previews;
    }

    private String buildPath(String l1, String l2, String l3) {
        List<String> parts = new ArrayList<>();
        if (l1 != null) parts.add(l1);
        if (l2 != null) parts.add(l2);
        if (l3 != null) parts.add(l3);
        return String.join(" / ", parts);
    }

    private boolean isPdf(MultipartFile file, String filename) {
        return "application/pdf".equals(file.getContentType())
                || filename.toLowerCase().endsWith(".pdf");
    }
}
