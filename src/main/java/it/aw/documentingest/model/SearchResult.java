package it.aw.documentingest.model;

/**
 * Singolo risultato di una ricerca semantica.
 */
public record SearchResult(
        double score,
        String text,
        String filename,
        String documentId
) {}
