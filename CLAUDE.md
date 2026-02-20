# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
mvn clean package       # Build the fat JAR (~151MB)
mvn spring-boot:run     # Run the application directly
mvn test                # Run tests (no tests currently exist)
```

The service runs on port **8889** by default.

## Architecture

Spring Boot 2.7.18 / Java 17 REST service for document ingestion and semantic search (RAG foundation). Three layers:

1. **Controller** (`DocumentController`) — single endpoint: `POST /api/documents/ingest` (multipart file upload)
2. **Service** (`IngestionService`) — five-step pipeline: detect file type → parse (PDF via PDFBox or plain text) → split into chunks (500 chars, 50 overlap) → generate embeddings → store
3. **Config** (`LangChain4jConfig`) — wires two Spring beans: `AllMiniLmL6V2QuantizedEmbeddingModel` (local, no external API) and `InMemoryEmbeddingStore`

The embedding store is **in-memory and ephemeral** — data is lost on restart. Comments in `LangChain4jConfig` suggest `PgVectorEmbeddingStore` or `ChromaEmbeddingStore` for production.

LangChain4j version: **0.31.0**. All AI processing is local (no external API keys required).

Source packages under `src/main/java/it/aw/documentingest/`: `config`, `controller`, `service`.

## Key Configuration

`src/main/resources/application.properties`:
- `server.port=8889`
- Max upload size: 50MB (`spring.servlet.multipart.*`)
- Debug logging for `it.aw.documentingest`

## Testing the Endpoint

```bash
curl -X POST http://localhost:8889/api/documents/ingest -F "file=@documento.pdf"
```
