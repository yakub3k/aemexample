package com.example.core.services;

/**
 * OSGi service for automated land registry (Księgi Wieczyste) search
 * via the Ministry of Justice portal (przegladarka-ekw.ms.gov.pl).
 */
public interface EkwSearchService {

    /**
     * Search for a land registry entry by its number components.
     * The check digit (cyfra kontrolna) is calculated automatically using the
     * land registry checksum algorithm.
     *
     * @param kwDepartment court department code (e.g. "LU1I")
     * @param kwNumber registry number (e.g. "00016057")
     * @return EkwSearchResult containing the response HTML and status
     */
    EkwSearchResult searchKW(String kwDepartment, String kwNumber);


    /**
     * Calculate the check digit (checksum) for a land registry number.
     * The algorithm uses weights [1, 3, 7] cyclically applied to the 4-character
     * court code and 8-digit registry number. Letters are mapped to digits using
     * the formula: ((alphabetPosition - 1) % 9) + 1, digits remain unchanged.
     * The check digit is the weighted sum modulo 10.
     *
     * @param kwDepartment court department code (e.g. "LU1I")
     * @param kwNumber registry number (e.g. "00016057")
     * @return check digit as an integer (0-9)
     */
    int calculateCheckDigit(String kwDepartment, String kwNumber);

    /**
     * Result object for EKW search operations.
     */
    class EkwSearchResult {
        private final boolean success;
        private final String html;
        private final String message;

        public EkwSearchResult(boolean success, String html, String message) {
            this.success = success;
            this.html = html;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getHtml() {
            return html;
        }

        public String getMessage() {
            return message;
        }
    }
}
