package it.aw.documentingest.config;

import dev.langchain4j.community.store.embedding.duckdb.DuckDBEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configura i bean LangChain4j.
 *
 * EmbeddingModel: AllMiniLM-L6-v2 quantizzato — gira in locale, senza API key.
 * EmbeddingStore: DuckDBEmbeddingStore — database embedded, nessun server esterno.
 *                 Persiste su file .duckdb; crash-safe, con indici vettoriali nativi.
 *                 Per produzione sostituire con PgVectorEmbeddingStore.
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${store.embedding.path}")
    private String embeddingFilePath;

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Inizializzazione EmbeddingModel: AllMiniLmL6V2Quantized (locale)");
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() throws IOException {
        Path path = Paths.get(embeddingFilePath);
        Files.createDirectories(path.toAbsolutePath().getParent());
        log.info("EmbeddingStore: DuckDB su file {}", path.toAbsolutePath());
        return DuckDBEmbeddingStore.builder()
                .filePath(embeddingFilePath)
                .build();
    }
}
