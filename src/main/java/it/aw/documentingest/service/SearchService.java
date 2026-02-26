package it.aw.documentingest.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import it.aw.documentingest.model.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Esegue ricerche semantiche sull'embedding store.
 * <p>
 * I chunk orfani non esistono più: la cancellazione è fisica, quindi non è necessario
 * alcun filtraggio post-query sul registry.
 */
@Service
public class SearchService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public SearchService(EmbeddingModel embeddingModel,
                         EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public List<SearchResult> search(String query, int limit, String projectId) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(limit);
        if (projectId != null && !projectId.isBlank()) {
            builder.filter(new IsEqualTo("projectId", projectId));
        }
        List<EmbeddingMatch<TextSegment>> candidates = embeddingStore.search(builder.build()).matches();

        return candidates.stream()
                .map(m -> {
                    var meta = m.embedded().metadata();
                    String  sectionPath  = meta.getString("section.path");
                    String  sectionTitle = meta.getString("section.title");
                    Integer sectionLevel = meta.getInteger("section.level");
                    Integer pageStart    = meta.getInteger("chunk.page_start");
                    Integer pageEnd      = meta.getInteger("chunk.page_end");
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

}
