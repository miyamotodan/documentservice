package it.aw.documentingest.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rileva heading strutturali nel testo di un documento usando pattern espliciti
 * e conservativi (solo numerazioni e keyword esplicite, nessuna euristica sulle
 * MAIUSCOLE generiche).
 * <p>
 * Livelli riconosciuti:
 * <ul>
 *   <li>Livello 1: "Capitolo N", "CAPITOLO N", "1. Titolo"</li>
 *   <li>Livello 2: "Art. N", "Articolo N", "Sezione N", "1.2 Titolo"</li>
 *   <li>Livello 3: "Comma N", "c. N", "1.2.3 Titolo"</li>
 * </ul>
 *
 * Il rilevamento opera riga per riga sul testo completo del documento.
 * L'offset di ogni heading corrisponde alla posizione del primo carattere
 * della riga nell'intero testo.
 */
public class SectionDetector {

    /**
     * Una sezione rilevata: posizione nel testo, profondità e titolo.
     * L'offset è 0-based rispetto all'inizio del testo completo.
     */
    public record SectionBoundary(int offset, int level, String title) {}

    // Livello 1
    private static final Pattern L1_KEYWORD = Pattern.compile(
            "^\\s*(Capitolo|CAPITOLO)\\s+\\d+.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern L1_NUMBERED = Pattern.compile(
            "^\\s*(\\d+)\\.\\s+[A-ZÀÈÉÌÒÙ].*");

    // Livello 2
    private static final Pattern L2_KEYWORD = Pattern.compile(
            "^\\s*(Art\\.|Articolo|Sezione)\\s+\\d+.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern L2_NUMBERED = Pattern.compile(
            "^\\s*(\\d+\\.\\d+)\\s+[A-ZÀÈÉÌÒÙ].*");

    // Livello 3
    private static final Pattern L3_KEYWORD = Pattern.compile(
            "^\\s*(Comma|c\\.)\\s+\\d+.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern L3_NUMBERED = Pattern.compile(
            "^\\s*(\\d+\\.\\d+\\.\\d+)\\s+[A-ZÀÈÉÌÒÙ].*");

    private SectionDetector() {}

    /**
     * Analizza il testo e restituisce la lista di heading rilevati,
     * ordinati per posizione crescente nel testo.
     *
     * @param text testo completo del documento
     * @return lista di {@link SectionBoundary}, vuota se nessun heading trovato
     */
    public static List<SectionBoundary> detect(String text) {
        List<SectionBoundary> boundaries = new ArrayList<>();
        int offset = 0;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                int level = matchLevel(line);
                if (level > 0) {
                    boundaries.add(new SectionBoundary(offset, level, trimmed));
                }
            }
            offset += line.length() + 1; // +1 per il '\n'
        }
        return boundaries;
    }

    /**
     * Determina il livello gerarchico di una riga, oppure 0 se non è un heading.
     */
    private static int matchLevel(String line) {
        if (L3_NUMBERED.matcher(line).matches() || L3_KEYWORD.matcher(line).matches()) return 3;
        if (L2_NUMBERED.matcher(line).matches() || L2_KEYWORD.matcher(line).matches()) return 2;
        if (L1_NUMBERED.matcher(line).matches() || L1_KEYWORD.matcher(line).matches()) return 1;
        return 0;
    }

    /**
     * Dato un offset nel testo, restituisce la SectionBoundary attiva
     * (l'ultimo heading che precede o coincide con quell'offset).
     * Restituisce null se nessun heading precede l'offset.
     */
    public static SectionBoundary activeAt(int offset, List<SectionBoundary> boundaries) {
        SectionBoundary active = null;
        for (SectionBoundary b : boundaries) {
            if (b.offset() <= offset) active = b;
            else break;
        }
        return active;
    }

    /**
     * Costruisce il contesto gerarchico completo (L1/L2/L3) attivo
     * all'offset dato, guardando all'indietro nella lista dei boundaries.
     *
     * @return array di tre String: [l1Title, l2Title, l3Title], null se il livello non è attivo
     */
    public static String[] hierarchyAt(int offset, List<SectionBoundary> boundaries) {
        String[] h = new String[3]; // [l1, l2, l3]
        // Scansione all'indietro: per ogni livello cerco l'heading più recente prima dell'offset
        for (int i = boundaries.size() - 1; i >= 0; i--) {
            SectionBoundary b = boundaries.get(i);
            if (b.offset() > offset) continue;
            int idx = b.level() - 1; // level 1→0, 2→1, 3→2
            if (idx >= 0 && idx < 3 && h[idx] == null) {
                h[idx] = b.title();
            }
            // Se abbiamo trovato un livello inferiore a quello del boundary corrente,
            // i livelli superiori vengono azzerati (nuovo capitolo → nuova sezione)
            for (int lev = 0; lev < idx; lev++) {
                // Non azzerare: un capitolo non cancella un heading di livello superiore trovato prima
            }
            if (h[0] != null && h[1] != null && h[2] != null) break;
        }
        // Coerenza: se non c'è L1, L2 e L3 non hanno senso come "sotto-livelli"
        // ma li manteniamo comunque se presenti (documento che inizia direttamente con Art.)
        return h;
    }
}
