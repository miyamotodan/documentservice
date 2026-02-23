package it.aw.documentingest.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsatore PDF pagina per pagina via PDFBox.
 * <p>
 * Produce un {@link PagedText} contenente il testo completo del documento
 * e la mappa degli offset per sapere dove inizia e finisce ogni pagina
 * nel testo concatenato. Questa informazione viene usata da IngestionService
 * per calcolare pageStart/pageEnd di ogni chunk dopo il splitting.
 */
public class PdfPageParser {

    private static final Logger log = LoggerFactory.getLogger(PdfPageParser.class);

    private PdfPageParser() {}

    /**
     * Testo completo del PDF con mappa pagina → offset nel testo concatenato.
     *
     * @param fullText    testo di tutte le pagine concatenato
     * @param pageOffsets lista di {@code int[]{pageNumber, startOffset, endOffset}}
     *                    (pageNumber è 1-based)
     */
    public record PagedText(String fullText, List<int[]> pageOffsets) {

        /**
         * Calcola il page range che un chunk copre, dato il suo offset di inizio
         * e fine nel testo concatenato.
         *
         * @param chunkStart offset inizio chunk nel fullText
         * @param chunkEnd   offset fine chunk nel fullText (esclusivo)
         * @return int[]{pageStart, pageEnd} (1-based), oppure null se gli offset
         *         non corrispondono a nessuna pagina nota
         */
        public int[] pageRangeFor(int chunkStart, int chunkEnd) {
            int pageStart = -1;
            int pageEnd   = -1;
            for (int[] po : pageOffsets) {
                int page   = po[0];
                int start  = po[1];
                int end    = po[2];
                if (end <= chunkStart) continue;   // pagina prima del chunk
                if (start >= chunkEnd) break;      // pagina dopo il chunk
                if (pageStart == -1) pageStart = page;
                pageEnd = page;
            }
            if (pageStart == -1) return null;
            return new int[]{pageStart, pageEnd};
        }
    }

    /**
     * Esegue il parsing del PDF dall'input stream.
     * L'input stream NON viene chiuso dal metodo: la responsabilità è del chiamante.
     */
    public static PagedText parse(InputStream inputStream) throws IOException {
        try (PDDocument doc = PDDocument.load(inputStream)) {
            int totalPages = doc.getNumberOfPages();
            log.debug("PdfPageParser: {} pagine trovate", totalPages);

            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder sb = new StringBuilder();
            List<int[]> offsets = new ArrayList<>(totalPages);

            for (int p = 1; p <= totalPages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                int start = sb.length();
                sb.append(stripper.getText(doc));
                offsets.add(new int[]{p, start, sb.length()});
            }

            return new PagedText(sb.toString(), offsets);
        }
    }
}
