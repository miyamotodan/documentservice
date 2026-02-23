# Esempi di chiamate API — document-ingest-service

Base URL: `http://localhost:8889`

---

## Strutture dati

### `DocumentSummary` — risposta leggera (list, ingest, re-ingest)
```json
{
  "filename":     "documento.pdf",
  "documentId":   "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
  "ingestedAt":   "2026-02-19T10:30:00.123",
  "chunkCount":   14,
  "chunkSize":    500,
  "overlap":      50,
  "sectionCount": 3
}
```
> `sectionCount`: numero di heading distinti rilevati automaticamente (0 = documento piatto).

### `DocumentRecord` — risposta dettagliata (`GET /{filename}`)
```json
{
  "filename":     "documento.pdf",
  "documentId":   "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
  "ingestedAt":   "2026-02-19T10:30:00.123",
  "chunkCount":   14,
  "chunkSize":    500,
  "overlap":      50,
  "sectionCount": 3,
  "chunkPreviews": [
    {
      "index":        0,
      "sectionL1":    "Capitolo 1 — Disposizioni generali",
      "sectionL2":    "Art. 3 — Definizioni",
      "sectionL3":    null,
      "sectionTitle": "Art. 3 — Definizioni",
      "sectionPath":  "Capitolo 1 — Disposizioni generali / Art. 3 — Definizioni",
      "sectionLevel": 2,
      "pageStart":    4,
      "pageEnd":      5,
      "text":         "Per gli effetti del presente contratto si intende per «Parte» ciascuno dei soggetti..."
    }
  ]
}
```
> `pageStart`/`pageEnd`: null per file di testo (non-PDF).
> `sectionL1`/`sectionL2`/`sectionL3`: null se il livello non è presente nel documento.

### `SearchResult` — risultato di ricerca semantica
```json
{
  "score":        0.91,
  "text":         "Art. 12 — Rescissione del contratto. Le parti possono recedere...",
  "filename":     "contratto.pdf",
  "documentId":   "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
  "sectionPath":  "Capitolo 2 / Art. 12 — Rescissione",
  "sectionTitle": "Art. 12 — Rescissione",
  "sectionLevel": 2,
  "pageStart":    18,
  "pageEnd":      19
}
```

---

## 1. Indicizza un documento

**`POST /api/documents/ingest`**

```bash
# Con parametri di default (chunkSize=500, overlap=50)
curl -X POST http://localhost:8889/api/documents/ingest \
     -F "file=@/percorso/al/documento.pdf"

# Con chunking personalizzato
curl -X POST "http://localhost:8889/api/documents/ingest?chunkSize=300&overlap=30" \
     -F "file=@/percorso/al/documento.pdf"
```

Risposta `200 OK` — `DocumentSummary`:
```json
{
  "filename":     "documento.pdf",
  "documentId":   "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
  "ingestedAt":   "2026-02-19T10:30:00.123",
  "chunkCount":   14,
  "chunkSize":    500,
  "overlap":      50,
  "sectionCount": 3
}
```

> Parametri opzionali: `chunkSize` (min 50, default 500), `overlap` (≥ 0, < chunkSize, default 50).

---

## 2. Ricerca semantica

**`GET /api/documents/search?q={query}&limit={n}`**

```bash
curl "http://localhost:8889/api/documents/search?q=clausole+di+rescissione&limit=3"
```

Risposta `200 OK` — lista di `SearchResult`:
```json
[
  {
    "score":        0.91,
    "text":         "Art. 12 — Rescissione del contratto. Le parti possono recedere dal presente accordo...",
    "filename":     "contratto.pdf",
    "documentId":   "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
    "sectionPath":  "Capitolo 2 / Art. 12 — Rescissione",
    "sectionTitle": "Art. 12 — Rescissione",
    "sectionLevel": 2,
    "pageStart":    18,
    "pageEnd":      19
  },
  {
    "score":        0.84,
    "text":         "In caso di inadempimento, la parte lesa ha facoltà di risolvere il contratto...",
    "filename":     "clausole.txt",
    "documentId":   "b7d2e5f1-11c3-4a8b-8d20-3g6f7e4c2b1d",
    "sectionPath":  null,
    "sectionTitle": null,
    "sectionLevel": 0,
    "pageStart":    null,
    "pageEnd":      null
  }
]
```

> `limit` è opzionale (default: `5`).
> `sectionPath`/`sectionTitle` sono null per documenti piatti (nessun heading rilevato) o file di testo.

---

## 3. Lista tutti i documenti indicizzati

**`GET /api/documents`**

```bash
curl http://localhost:8889/api/documents
```

Risposta `200 OK` — lista di `DocumentSummary` (senza chunk preview):
```json
[
  {
    "filename":     "documento.pdf",
    "documentId":   "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
    "ingestedAt":   "2026-02-19T10:30:00.123",
    "chunkCount":   14,
    "chunkSize":    500,
    "overlap":      50,
    "sectionCount": 3
  },
  {
    "filename":     "clausole.txt",
    "documentId":   "b7d2e5f1-11c3-4a8b-8d20-3g6f7e4c2b1d",
    "ingestedAt":   "2026-02-19T11:05:42.456",
    "chunkCount":   6,
    "chunkSize":    500,
    "overlap":      50,
    "sectionCount": 0
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
  "totalChunks":    20,
  "storeType":      "DuckDB",
  "embeddingModel": "AllMiniLmL6V2Quantized",
  "ephemeral":      false
}
```

> `ephemeral: false` — i dati sono persistiti su file DuckDB e sopravvivono al riavvio.

---

## 5. Dettaglio di un documento

**`GET /api/documents/{filename}`**

```bash
curl http://localhost:8889/api/documents/documento.pdf
```

Risposta `200 OK` — `DocumentRecord` completo con anteprima chunk strutturata:
```json
{
  "filename":     "documento.pdf",
  "documentId":   "a3f1c2e4-88b0-4d7a-9c10-2f5e6d3b1a0c",
  "ingestedAt":   "2026-02-19T10:30:00.123",
  "chunkCount":   14,
  "chunkSize":    500,
  "overlap":      50,
  "sectionCount": 3,
  "chunkPreviews": [
    {
      "index":        0,
      "sectionL1":    "Capitolo 1 — Disposizioni generali",
      "sectionL2":    null,
      "sectionL3":    null,
      "sectionTitle": "Capitolo 1 — Disposizioni generali",
      "sectionPath":  "Capitolo 1 — Disposizioni generali",
      "sectionLevel": 1,
      "pageStart":    1,
      "pageEnd":      3,
      "text":         "Il presente contratto disciplina i rapporti tra le parti con riferimento..."
    },
    {
      "index":        0,
      "sectionL1":    "Capitolo 1 — Disposizioni generali",
      "sectionL2":    "Art. 3 — Definizioni",
      "sectionL3":    null,
      "sectionTitle": "Art. 3 — Definizioni",
      "sectionPath":  "Capitolo 1 — Disposizioni generali / Art. 3 — Definizioni",
      "sectionLevel": 2,
      "pageStart":    4,
      "pageEnd":      5,
      "text":         "Per gli effetti del presente contratto si intende per «Parte» ciascuno dei soggetti..."
    }
  ]
}
```

Risposta `404 Not Found` se il documento non è nel registro (corpo vuoto).

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

> I chunk fisici restano nel DuckDB embedding store ma vengono filtrati
> automaticamente da tutte le ricerche successive (strategia "orfani").

---

## 7. Aggiorna un documento (re-ingest)

**`PUT /api/documents/{filename}`**

Sostituisce il contenuto indicizzato di un documento con una nuova versione del file.
Il filename nel path è la chiave: il file caricato può avere un nome diverso.

```bash
# Con parametri di default
curl -X PUT http://localhost:8889/api/documents/documento.pdf \
     -F "file=@/percorso/al/documento_v2.pdf"

# Con chunking personalizzato
curl -X PUT "http://localhost:8889/api/documents/documento.pdf?chunkSize=300&overlap=30" \
     -F "file=@/percorso/al/documento_v2.pdf"
```

Risposta `200 OK` — `DocumentSummary` del nuovo indice:
```json
{
  "filename":     "documento.pdf",
  "documentId":   "f9a4b3c2-55d1-4e6f-a7b8-1c2d3e4f5a6b",
  "ingestedAt":   "2026-02-19T14:55:10.789",
  "chunkCount":   17,
  "chunkSize":    500,
  "overlap":      50,
  "sectionCount": 4
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

# 3. Cerca informazioni (risposta include sezione e pagina)
curl "http://localhost:8889/api/documents/search?q=penale+ritardo&limit=3"

# 4. Dettaglio completo con chunk strutturati
curl http://localhost:8889/api/documents/contratto.pdf

# 5. Aggiorna un documento
curl -X PUT http://localhost:8889/api/documents/contratto.pdf -F "file=@contratto_rev2.pdf"

# 6. Rimuovi un documento
curl -X DELETE http://localhost:8889/api/documents/note.txt

# 7. Verifica stato finale
curl http://localhost:8889/api/documents
```
