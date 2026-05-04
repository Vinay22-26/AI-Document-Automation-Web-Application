package com.vinay.backend.Service;

import com.vinay.backend.Ai.AiService;
import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ExtractedContentRepo;
import net.sourceforge.tess4j.Tesseract;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
public class FileService {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @Autowired
    private AiService aiService;

    public void extractAndSaveText(Long fileId, byte[] data, String email) {
        try {
            String rawExtractedText = "";
            Tika tika = new Tika();
            String mimeType = tika.detect(data);

            if (mimeType.startsWith("image/")) {
                rawExtractedText = doImageOCR(data);
            } else if (mimeType.equals("application/pdf")) {
                rawExtractedText = extractFromPDF(data);
            } else {
                rawExtractedText = extractWithTika(data);
            }

            rawExtractedText = normalizeText(rawExtractedText);

            if (rawExtractedText != null && !rawExtractedText.trim().isEmpty()) {
                String formattedText = aiService.formatText(rawExtractedText);

                ExtractedContent contentEntity = extractedContentRepo.findByFileId(fileId)
                        .orElse(new ExtractedContent());
                contentEntity.setFileId(fileId);
                contentEntity.setEmail(email);
                contentEntity.setContent(formattedText.trim());
                extractedContentRepo.save(contentEntity);
            }
        } catch (Throwable t) {
        }
    }

    private String extractFromPDF(byte[] data) {
        StringBuilder result = new StringBuilder();
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(data))) {
            PDFRenderer renderer = new PDFRenderer(document);
            Tesseract tesseract = getTesseract();

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 400);
                String ocrText = tesseract.doOCR(image);
                result.append(ocrText).append("\n");
            }
        } catch (Exception e) {
            result.append(extractWithTika(data));
        }
        return result.toString();
    }

    private String doImageOCR(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null)
                return "";
            return getTesseract().doOCR(image);
        } catch (Exception e) {
            return "";
        }
    }

    private String extractWithTika(byte[] data) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1);
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
                parser.parse(stream, handler, metadata, context);
                return handler.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private Tesseract getTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("D:/DocAutomation Project/BackEnd/tessdata");
        tesseract.setLanguage("eng");
        return tesseract;
    }

    private String normalizeText(String text) {
        if (text == null)
            return "";
        return text.replaceAll("[\\t\\n\\r]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}