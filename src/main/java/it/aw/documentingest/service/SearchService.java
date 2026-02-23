package it.aw.documentingest.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import it.aw.documentingest.model.SearchResult;
import it.aw.documentingest.registry.DocumentRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Esegue ricerche semantiche sull'embedding store.
 * <p>
 * Recupera un numero di candidati moltiplicato per SEARCH_MULTIPLIER,
 * filtra i chunk di documenti non più attivi nel registry (orfani),
 * e arricchisce ogni risultato con i metadati di sezione e pagina.
 */
@Service
public class SearchService {

    private static final int SEARCH_MULTIPLIER = 5;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentRegistry registry;

    public SearchService(EmbeddingModel embeddingModel,
                         EmbeddingStore<TextSegment> embeddingStore,
                         DocumentRegistry registry) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.registry = registry;
    }

    public List<SearchResult> search(String query, int limit) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> candidates = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(limit * SEARCH_MULTIPLIER)
                        .build()
        ).matches();

        return candidates.stream()
                .filter(m -> {
                    String docId = m.embedded().metadata().getString("documentId");
                    return docId != null && registry.isActiveDocumentId(docId);
                })
                .limit(limit)
                .map(m -> {
                    var meta = m.embedded().metadata();
                    String  sectionPath  = meta.getString("section.path");
                    String  sectionTitle = meta.getString("section.title");
                    Integer sectionLevel = toInteger(meta.getString("section.level"));
                    Integer pageStart    = toInteger(meta.getString("chunk.page_start"));
                    Integer pageEnd      = toInteger(meta.getString("chunk.page_end"));
                    return new SearchResult(
                            m.score(),
                            m.embedded().text(),
                            meta.getString("filename"),
                            meta.getString("documentId"),
                            sectionPath,
                            sectionTitle,
                            sectionLevel != null ? sectionLevel : 0,
                            pageStart,
                            pageEnd
                    );
                })
                .collect(Collectors.toList());
    }

    private Integer toInteger(String value) {
        if (value == null) return null;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return null; }
    }
}
