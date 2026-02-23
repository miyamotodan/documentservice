package it.aw.documentingest.model;

/**
 * Risultato di una ricerca semantica.
 * Contiene il testo del chunk, il documento di origine, il punteggio di similarità
 * e i metadati di posizione (sezione e pagina) per contestualizzare il risultato.
 */
public record SearchResult(
        double  score,
        String  text,
        String  filename,
        String  documentId,
        String  sectionPath,    // breadcrumb "Capitolo 1 / Art. 3" (null se documento piatto)
        String  sectionTitle,   // titolo della sezione foglia (null se documento piatto)
        int     sectionLevel,   // profondità della sezione (0 = documento piatto)
        Integer pageStart,      // prima pagina del chunk (null per non-PDF)
        Integer pageEnd         // ultima pagina del chunk (null per non-PDF)
) {}
