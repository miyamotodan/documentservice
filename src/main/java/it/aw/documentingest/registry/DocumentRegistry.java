package it.aw.documentingest.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.aw.documentingest.model.DocumentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro thread-safe dei documenti indicizzati.
 * <p>
 * All'avvio carica lo stato da file JSON (se esiste).
 * Il salvataggio su disco avviene allo shutdown tramite StoreLifecycle.
 * <p>
 * L'InMemoryEmbeddingStore non offre API di listing o cancellazione,
 * quindi questo registry funge da catalogo parallelo. I chunk di documenti
 * rimossi o ri-indicizzati restano fisicamente nello store ma vengono
 * filtrati in fase di ricerca tramite il documentId.
 */
@Component
public class DocumentRegistry {

    private static final Logger log = LoggerFactory.getLogger(DocumentRegistry.class);

    private final Map<String, DocumentRecord> records = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    @Value("${store.registry.file}")
    private String registryFilePath;

    public DocumentRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadFromFile() {
        Path path = Paths.get(registryFilePath);
        if (!Files.exists(path)) {
            log.info("DocumentRegistry: file {} non trovato, partenza da zero.", path.toAbsolutePath());
            return;
        }
        try {
            TypeReference<Map<String, DocumentRecord>> typeRef = new TypeReference<>() {};
            Map<String, DocumentRecord> loaded = objectMapper.readValue(path.toFile(), typeRef);
            records.putAll(loaded);
            log.info("DocumentRegistry: caricati {} documenti da {}", loaded.size(), path.toAbsolutePath());
        } catch (IOException e) {
            log.error("DocumentRegistry: impossibile caricare il file {}: {}", path.toAbsolutePath(), e.getMessage());
        }
    }

    public void register(DocumentRecord record) {
        records.put(record.filename(), record);
    }

    public Optional<DocumentRecord> findByFilename(String filename) {
        return Optional.ofNullable(records.get(filename));
    }

    public List<DocumentRecord> findAll() {
        return new ArrayList<>(records.values());
    }

    public boolean remove(String filename) {
        return records.remove(filename) != null;
    }

    /** Verifica se il documentId dato appartiene a un documento attualmente attivo. */
    public boolean isActiveDocumentId(String documentId) {
        return records.values().stream()
                .anyMatch(r -> r.documentId().equals(documentId));
    }

    public int totalDocuments() {
        return records.size();
    }

    public int totalChunks() {
        return records.values().stream().mapToInt(DocumentRecord::chunkCount).sum();
    }

    /** Espone una vista non modificabile della mappa per la serializzazione. */
    public Map<String, DocumentRecord> getRecordsMap() {
        return Collections.unmodifiableMap(records);
    }
}
