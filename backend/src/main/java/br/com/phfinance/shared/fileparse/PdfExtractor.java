package br.com.phfinance.shared.fileparse;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Component
public class PdfExtractor {

    private final Tesseract tesseract;

    @Autowired
    public PdfExtractor(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    /**
     * Extracts text from a PDF using PDFBox.
     * Suitable for text-based PDFs (Nubank, Neon).
     *
     * @param pdf raw PDF bytes
     * @return extracted text content
     * @throws PdfExtractionException if extraction fails
     */
    public String extractText(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new PdfExtractionException("PDF bytes must not be null or empty");
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            throw new PdfExtractionException("Failed to extract text from PDF", e);
        }
    }

    /**
     * Extracts text from a PDF using Tesseract OCR as fallback.
     * Suitable for scanned PDFs (Inter).
     *
     * @param pdf raw PDF bytes
     * @return OCR-extracted text content
     * @throws PdfExtractionException if OCR extraction fails
     */
    public String extractTextOcr(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new PdfExtractionException("PDF bytes must not be null or empty");
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);

            StringBuilder result = new StringBuilder();
            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String pageText = tesseract.doOCR(image);
                result.append(pageText);
                if (i < pageCount - 1) {
                    result.append("\n");
                }
            }
            return result.toString();
        } catch (IOException e) {
            throw new PdfExtractionException("Failed to load PDF for OCR", e);
        } catch (TesseractException e) {
            throw new PdfExtractionException("Failed to extract text from PDF", e);
        }
    }
}
