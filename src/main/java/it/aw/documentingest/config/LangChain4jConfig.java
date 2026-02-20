package it.aw.documentingest.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configura i bean LangChain4j.
 *
 * EmbeddingModel: AllMiniLM-L6-v2 quantizzato — gira in locale, senza API key.
 * EmbeddingStore:  InMemoryEmbeddingStore con persistenza su file JSON.
 *                  All'avvio carica il file se esiste, altrimenti parte da zero.
 *                  Il salvataggio su disco avviene allo shutdown tramite StoreLifecycle.
 *                  Per produzione sostituire con PgVectorEmbeddingStore.
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${store.embedding.file}")
    private String embeddingFilePath;

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Inizializzazione EmbeddingModel: AllMiniLmL6V2Quantized (locale)");
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore() {
        Path path = Paths.get(embeddingFilePath);
        if (Files.exists(path)) {
            log.info("EmbeddingStore: caricamento da file {}", path.toAbsolutePath());
            return InMemoryEmbeddingStore.fromFile(path);
        }
        log.info("EmbeddingStore: file {} non trovato, partenza da zero.", path.toAbsolutePath());
        return new InMemoryEmbeddingStore<>();
    }
}
