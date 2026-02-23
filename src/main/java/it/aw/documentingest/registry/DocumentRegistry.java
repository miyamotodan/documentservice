package it.aw.documentingest.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.aw.documentingest.model.ChunkInfo;
import it.aw.documentingest.model.DocumentRecord;
import it.aw.documentingest.model.DocumentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registro dei documenti indicizzati, persistito nella tabella {@code documents}
 * dello stesso file DuckDB usato dall'embedding store.
 * <p>
 * Un'unica connessione JDBC è condivisa da tutte le operazioni; l'accesso
 * è sincronizzato per garantire la thread-safety (DuckDBConnection non è thread-safe).
 * La persistenza è automatica: non è richiesto alcun salvataggio esplicito a shutdown.
 * <p>
 * Migrazione schema: se all'avvio la colonna {@code section_count} non esiste
 * (schema v1 senza info di sezione), la tabella viene ricreata con lo schema corrente.
 * I documenti esistenti devono essere re-indicizzati.
 */
@Component
public class DocumentRegistry {

    private static final Logger log = LoggerFactory.getLogger(DocumentRegistry.class);

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS documents (
                filename       VARCHAR   PRIMARY KEY,
                document_id    VARCHAR   NOT NULL,
                ingested_at    TIMESTAMP NOT NULL,
                chunk_count    INTEGER   NOT NULL,
                chunk_size     INTEGER   NOT NULL,
                overlap        INTEGER   NOT NULL,
                section_count  INTEGER   NOT NULL DEFAULT 0,
                chunk_previews VARCHAR   NOT NULL
            )
            """;

    private static final TypeReference<List<ChunkInfo>> CHUNK_LIST_TYPE = new TypeReference<>() {};

    @Value("${store.embedding.path}")
    private String dbPath;

    private final ObjectMapper objectMapper;
    private Connection conn;

    public DocumentRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() throws SQLException, IOException {
        Path path = Paths.get(dbPath);
        Files.createDirectories(path.toAbsolutePath().getParent());
        conn = DriverManager.getConnection("jdbc:duckdb:" + path.toAbsolutePath());
        migrateIfNeeded();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        }
        log.info("DocumentRegistry: tabella 'documents' pronta su {}", path.toAbsolutePath());
    }

    /** Rileva schema obsoleto (senza section_count) e ricrea la tabella se necessario. */
    private void migrateIfNeeded() throws SQLException {
        boolean hasColumn;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'documents' AND column_name = 'section_count'")) {
            try (ResultSet rs = ps.executeQuery()) {
                hasColumn = rs.next();
            }
        }
        if (!hasColumn) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS documents");
            }
            log.warn("DocumentRegistry: schema obsoleto rilevato — tabella 'documents' ricreata. " +
                     "Re-indicizzare i documenti esistenti.");
        }
    }

    @PreDestroy
    void close() {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            log.warn("Errore chiusura connessione DuckDB registry: {}", e.getMessage());
        }
    }

    public synchronized void register(DocumentRecord record) {
        String sql = """
                INSERT INTO documents
                    (filename, document_id, ingested_at, chunk_count, chunk_size, overlap, section_count, chunk_previews)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (filename) DO UPDATE SET
                    document_id    = EXCLUDED.document_id,
                    ingested_at    = EXCLUDED.ingested_at,
                    chunk_count    = EXCLUDED.chunk_count,
                    chunk_size     = EXCLUDED.chunk_size,
                    overlap        = EXCLUDED.overlap,
                    section_count  = EXCLUDED.section_count,
                    chunk_previews = EXCLUDED.chunk_previews
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.filename());
            ps.setString(2, record.documentId());
            ps.setTimestamp(3, Timestamp.valueOf(record.ingestedAt()));
            ps.setInt(4, record.chunkCount());
            ps.setInt(5, record.chunkSize());
            ps.setInt(6, record.overlap());
            ps.setInt(7, record.sectionCount());
            ps.setString(8, objectMapper.writeValueAsString(record.chunkPreviews()));
            ps.executeUpdate();
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Errore salvataggio documento nel registry", e);
        }
    }

    public synchronized Optional<DocumentRecord> findByFilename(String filename) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM documents WHERE filename = ?")) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(toRecord(rs));
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Errore lettura documento dal registry", e);
        }
        return Optional.empty();
    }

    public synchronized List<DocumentRecord> findAll() {
        List<DocumentRecord> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM documents ORDER BY ingested_at DESC")) {
            while (rs.next()) result.add(toRecord(rs));
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Errore lettura registry", e);
        }
        return result;
    }

    public synchronized List<DocumentSummary> findAllAsSummary() {
        List<DocumentSummary> result = new ArrayList<>();
        String sql = "SELECT filename, document_id, ingested_at, chunk_count, chunk_size, overlap, section_count " +
                     "FROM documents ORDER BY ingested_at DESC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(toSummary(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Errore lettura registry (summary)", e);
        }
        return result;
    }

    public synchronized boolean remove(String filename) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM documents WHERE filename = ?")) {
            ps.setString(1, filename);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Errore rimozione documento dal registry", e);
        }
    }

    /** Verifica se il documentId dato appartiene a un documento attualmente attivo. */
    public synchronized boolean isActiveDocumentId(String documentId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM documents WHERE document_id = ? LIMIT 1")) {
            ps.setString(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore verifica documentId nel registry", e);
        }
    }

    public synchronized int totalDocuments() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM documents")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Errore conteggio documenti", e);
        }
    }

    public synchronized int totalChunks() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(chunk_count), 0) FROM documents")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Errore conteggio chunk", e);
        }
    }

    private DocumentRecord toRecord(ResultSet rs) throws SQLException, IOException {
        return new DocumentRecord(
                rs.getString("filename"),
                rs.getString("document_id"),
                rs.getTimestamp("ingested_at").toLocalDateTime(),
                rs.getInt("chunk_count"),
                rs.getInt("chunk_size"),
                rs.getInt("overlap"),
                rs.getInt("section_count"),
                objectMapper.readValue(rs.getString("chunk_previews"), CHUNK_LIST_TYPE)
        );
    }

    private DocumentSummary toSummary(ResultSet rs) throws SQLException {
        return new DocumentSummary(
                rs.getString("filename"),
                rs.getString("document_id"),
                rs.getTimestamp("ingested_at").toLocalDateTime(),
                rs.getInt("chunk_count"),
                rs.getInt("chunk_size"),
                rs.getInt("overlap"),
                rs.getInt("section_count")
        );
    }
}
