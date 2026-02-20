package it.aw.documentingest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import it.aw.documentingest.registry.DocumentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Gestisce il salvataggio su disco dell'embedding store e del document registry
 * allo shutdown dell'applicazione.
 * <p>
 * Il caricamento all'avvio è delegato ai singoli bean:
 *   - InMemoryEmbeddingStore → LangChain4jConfig.embeddingStore()
 *   - DocumentRegistry       → DocumentRegistry.loadFromFile()
 */
@Component
public class StoreLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StoreLifecycle.class);

    @Value("${store.embedding.file}")
    private String embeddingFilePath;

    @Value("${store.registry.file}")
    private String registryFilePath;

    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final DocumentRegistry registry;
    private final ObjectMapper objectMapper;

    public StoreLifecycle(InMemoryEmbeddingStore<TextSegment> embeddingStore,
                          DocumentRegistry registry,
                          ObjectMapper objectMapper) {
        this.embeddingStore = embeddingStore;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void save() {
        log.info("Shutdown: salvataggio store e registry su disco...");
        saveEmbeddingStore();
        saveRegistry();
    }

    private void saveEmbeddingStore() {
        Path path = Paths.get(embeddingFilePath);
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            embeddingStore.serializeToFile(path);
            log.info("EmbeddingStore salvato: {}", path.toAbsolutePath());
        } catch (IOException e) {
            log.error("Impossibile salvare l'EmbeddingStore su {}: {}", path.toAbsolutePath(), e.getMessage());
        }
    }

    private void saveRegistry() {
        Path path = Paths.get(registryFilePath);
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            objectMapper.writeValue(path.toFile(), registry.getRecordsMap());
            log.info("DocumentRegistry salvato: {} ({} documenti)", path.toAbsolutePath(), registry.totalDocuments());
        } catch (IOException e) {
            log.error("Impossibile salvare il DocumentRegistry su {}: {}", path.toAbsolutePath(), e.getMessage());
        }
    }
}
