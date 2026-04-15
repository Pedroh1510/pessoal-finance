package br.com.phfinance.shared;

import br.com.phfinance.shared.fileparse.PdfExtractionException;
import br.com.phfinance.shared.fileparse.PdfExtractor;
import net.sourceforge.tess4j.Tesseract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfExtractorTest {

    private PdfExtractor pdfExtractor;

    private static final Path NUBANK_PDF = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/nubank/NU_143766978_01JAN2026_31JAN2026.pdf");
    private static final Path NEON_PDF = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/neon/Statement6391180046951199824747284281849d1b2df.pdf");
    private static final Path INTER_PDF = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/inter/Extrato-14-03-2026-a-14-04-2026-PDF.pdf");

    @BeforeEach
    void setUp() {
        pdfExtractor = new PdfExtractor();
    }

    // --- extractText() ---

    @Test
    void extractText_nubankPdf_returnsNonEmptyString() throws IOException {
        assumeTrue(Files.exists(NUBANK_PDF), "Nubank PDF not found, skipping");
        byte[] pdfBytes = Files.readAllBytes(NUBANK_PDF);

        String text = pdfExtractor.extractText(pdfBytes);

        assertThat(text).isNotBlank();
    }

    @Test
    void extractText_nubankPdf_containsExpectedContent() throws IOException {
        assumeTrue(Files.exists(NUBANK_PDF), "Nubank PDF not found, skipping");
        byte[] pdfBytes = Files.readAllBytes(NUBANK_PDF);

        String text = pdfExtractor.extractText(pdfBytes);

        // Nubank statements contain bank name or account-related keywords
        assertThat(text.toLowerCase()).containsAnyOf("nubank", "nu", "fatura", "cartão", "extrato");
    }

    @Test
    void extractText_neonPdf_returnsNonEmptyString() throws IOException {
        assumeTrue(Files.exists(NEON_PDF), "Neon PDF not found, skipping");
        byte[] pdfBytes = Files.readAllBytes(NEON_PDF);

        String text = pdfExtractor.extractText(pdfBytes);

        assertThat(text).isNotBlank();
    }

    @Test
    void extractText_nullBytes_throwsException() {
        assertThatThrownBy(() -> pdfExtractor.extractText(null))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void extractText_emptyBytes_throwsException() {
        assertThatThrownBy(() -> pdfExtractor.extractText(new byte[0]))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void extractText_invalidBytes_throwsException() {
        byte[] garbage = "this is not a pdf".getBytes();

        assertThatThrownBy(() -> pdfExtractor.extractText(garbage))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("Failed to extract text");
    }

    // --- extractTextOcr() ---

    @Test
    void extractTextOcr_nullBytes_throwsException() {
        assertThatThrownBy(() -> pdfExtractor.extractTextOcr(null))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void extractTextOcr_emptyBytes_throwsException() {
        assertThatThrownBy(() -> pdfExtractor.extractTextOcr(new byte[0]))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void extractTextOcr_invalidBytes_throwsException() {
        byte[] garbage = "this is not a pdf".getBytes();

        assertThatThrownBy(() -> pdfExtractor.extractTextOcr(garbage))
                .isInstanceOf(PdfExtractionException.class);
    }

    @Test
    void extractTextOcr_interPdf_returnsNonEmptyString() throws IOException {
        assumeTrue(Files.exists(INTER_PDF), "Inter PDF not found, skipping");
        assumeTrue(isTesseractAvailable(), "Tesseract not available, skipping OCR test");

        byte[] pdfBytes = Files.readAllBytes(INTER_PDF);

        String text = pdfExtractor.extractTextOcr(pdfBytes);

        assertThat(text).isNotBlank();
    }

    /**
     * Checks whether Tesseract native library is available on the system.
     * Performs an actual OCR call on a trivial image to confirm the library loads.
     */
    private boolean isTesseractAvailable() {
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setLanguage("por");
            // Use a 1x1 blank BufferedImage to probe the native library without real data
            java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(
                    10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
            tesseract.doOCR(probe);
            return true;
        } catch (UnsatisfiedLinkError | Exception e) {
            return false;
        }
    }
}
