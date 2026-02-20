# Esempi di chiamate API — document-ingest-service

Base URL: `http://localhost:8889`

---

## 1. Indicizza un documento

**`POST /api/documents/ingest`**

```bash
curl -X POST http://localhost:8889/api/documents/ingest \
     -F "file=@/percorso/al/documento.pdf"
```

Risposta `200 OK`:
```json
{
  "filename": "documento.pdf",
  "documentId": "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
  "ingestedAt": "2026-02-19T10:30:00.123",
  "chunkCount": 14,
  "chunkPreviews": [
    "Primo paragrafo del documento. Il contenuto inizia con una breve introduzione...",
    "Secondo chunk contenente la sezione successiva del testo originale..."
  ]
}
```

---

## 2. Ricerca semantica

**`GET /api/documents/search?q={query}&limit={n}`**

```bash
curl "http://localhost:8889/api/documents/search?q=clausole+di+rescissione&limit=3"
```

Risposta `200 OK`:
```json
[
  {
    "score": 0.91,
    "text": "Art. 12 - Rescissione del contratto. Le parti possono recedere dal presente accordo...",
    "filename": "contratto.pdf",
    "documentId": "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c"
  },
  {
    "score": 0.84,
    "text": "In caso di inadempimento, la parte lesa ha facoltà di risolvere il contratto...",
    "filename": "clausole.txt",
    "documentId": "b7d2e5f1-11c3-4a8b-8d20-3g6f7e4c2b1d"
  },
  {
    "score": 0.76,
    "text": "Le condizioni di recesso anticipato sono disciplinate dall'articolo 15...",
    "filename": "contratto.pdf",
    "documentId": "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c"
  }
]
```

Il parametro `limit` è opzionale (default: `5`).

---

## 3. Lista tutti i documenti indicizzati

**`GET /api/documents`**

```bash
curl http://localhost:8889/api/documents
```

Risposta `200 OK`:
```json
[
  {
    "filename": "documento.pdf",
    "documentId": "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
    "ingestedAt": "2026-02-19T10:30:00.123",
    "chunkCount": 14,
    "chunkPreviews": [
      "Primo paragrafo del documento...",
      "Secondo chunk del documento..."
    ]
  },
  {
    "filename": "clausole.txt",
    "documentId": "b7d2e5f1-11c3-4a8b-8d20-3g6f7e4c2b1d",
    "ingestedAt": "2026-02-19T11:05:42.456",
    "chunkCount": 6,
    "chunkPreviews": [
      "Clausola 1 - Oggetto del contratto...",
      "Clausola 2 - Durata e rinnovo..."
    ]
  }
]
```

Lista vuota se nessun documento è stato ancora indicizzato:
```json
[]
```

---

## 4. Statistiche dello store

**`GET /api/documents/stats`**

```bash
curl http://localhost:8889/api/documents/stats
```

Risposta `200 OK`:
```json
{
  "totalDocuments": 2,
  "totalChunks": 20,
  "storeType": "InMemory",
  "embeddingModel": "AllMiniLmL6V2Quantized",
  "ephemeral": true
}
```

> `ephemeral: true` ricorda che i dati vengono persi al riavvio del servizio.

---

## 5. Dettaglio di un documento

**`GET /api/documents/{filename}`**

```bash
curl http://localhost:8889/api/documents/documento.pdf
```

Risposta `200 OK`:
```json
{
  "filename": "documento.pdf",
  "documentId": "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
  "ingestedAt": "2026-02-19T10:30:00.123",
  "chunkCount": 14,
  "chunkPreviews": [
    "Primo paragrafo del documento. Il contenuto inizia con una breve...",
    "Secondo chunk contenente la sezione successiva del testo originale...",
    "Terzo chunk con ulteriore contenuto estratto dal documento originale..."
  ]
}
```

Risposta `404 Not Found` se il documento non è nel registro:
```json
(corpo vuoto)
```

Per filename con spazi, usare l'encoding URL:
```bash
curl "http://localhost:8889/api/documents/mio%20documento.pdf"
```

---

## 6. Cancella un documento

**`DELETE /api/documents/{filename}`**

```bash
curl -X DELETE http://localhost:8889/api/documents/documento.pdf
```

Risposta `204 No Content` se rimosso con successo (corpo vuoto).

Risposta `404 Not Found` se il documento non esiste nel registro.

> I chunk fisici restano nell'InMemoryEmbeddingStore ma vengono filtrati
> automaticamente da tutte le ricerche successive.

---

## 7. Aggiorna un documento (re-ingest)

**`PUT /api/documents/{filename}`**

Sostituisce il contenuto indicizzato di un documento con una nuova versione del file.
Il filename nel path è la chiave: il file caricato può avere un nome diverso.

```bash
curl -X PUT http://localhost:8889/api/documents/documento.pdf \
     -F "file=@/percorso/al/documento_v2.pdf"
```

Risposta `200 OK` con il nuovo record:
```json
{
  "filename": "documento.pdf",
  "documentId": "f9a4b3c2-55d1-4e6f-a7b8-1c2d3e4f5a6b",
  "ingestedAt": "2026-02-19T14:55:10.789",
  "chunkCount": 17,
  "chunkPreviews": [
    "Versione aggiornata del documento. Introduzione rivista...",
    "Nuova sezione aggiunta nella versione 2 del documento..."
  ]
}
```

Risposta `404 Not Found` se il documento non era precedentemente indicizzato
(usare `POST /ingest` per la prima indicizzazione).

---

## Flusso tipico

```bash
# 1. Indicizza due documenti
curl -X POST http://localhost:8889/api/documents/ingest -F "file=@contratto.pdf"
curl -X POST http://localhost:8889/api/documents/ingest -F "file=@note.txt"

# 2. Verifica cosa è stato indicizzato
curl http://localhost:8889/api/documents/stats

# 3. Cerca informazioni
curl "http://localhost:8889/api/documents/search?q=penale+ritardo&limit=3"

# 4. Aggiorna un documento
curl -X PUT http://localhost:8889/api/documents/contratto.pdf -F "file=@contratto_rev2.pdf"

# 5. Rimuovi un documento
curl -X DELETE http://localhost:8889/api/documents/note.txt

# 6. Verifica stato finale
curl http://localhost:8889/api/documents
```
