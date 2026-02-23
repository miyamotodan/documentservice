package it.aw.documentingest.model;

/**
 * Metadati e anteprima di un singolo chunk indicizzato.
 * <p>
 * I campi sectionL1/L2/L3 rappresentano i titoli dei livelli gerarchici rilevati
 * nel documento (es. Capitolo → Art. → Comma). I valori null indicano assenza
 * di quel livello nel documento o nella sezione del chunk.
 * <p>
 * pageStart e pageEnd sono valorizzati solo per documenti PDF con layer testuale;
 * sono null per file di testo plain.
 */
public record ChunkInfo(
        int     index,         // posizione 0-based del chunk nella sua sezione foglia
        String  sectionL1,     // titolo livello 1 (es. "Capitolo 1") — null se assente
        String  sectionL2,     // titolo livello 2 (es. "Art. 3")     — null se assente
        String  sectionL3,     // titolo livello 3 (es. "Comma 1")    — null se assente
        String  sectionTitle,  // titolo della sezione foglia (copia del livello più profondo)
        String  sectionPath,   // breadcrumb "L1 / L2 / L3"
        int     sectionLevel,  // profondità: 0=piatto, 1=solo L1, 2=L1+L2, 3=L1+L2+L3
        Integer pageStart,     // prima pagina del chunk (null per non-PDF)
        Integer pageEnd,       // ultima pagina del chunk (null per non-PDF)
        String  text           // anteprima testo (max 150 caratteri)
) {}
