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
 * quindi filtra i chunk di documenti non più attivi nel registry
 * (rimossi o sostituiti da un re-ingest). In questo modo la cancellazione
 * logica di un documento si riflette immediatamente nei risultati di ricerca
 * senza dover ricostruire l'InMemoryEmbeddingStore.
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

    /**
     * Cerca i chunk più simili alla query testuale.
     *
     * @param query testo della query
     * @param limit numero massimo di risultati da restituire
     * @return lista di risultati ordinati per score decrescente
     */
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
                .map(m -> new SearchResult(
                        m.score(),
                        m.embedded().text(),
                        m.embedded().metadata().getString("filename"),
                        m.embedded().metadata().getString("documentId")
                ))
                .collect(Collectors.toList());
    }
}
