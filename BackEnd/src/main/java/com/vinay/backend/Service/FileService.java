package com.vinay.backend.Service;

import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ExtractedContentRepo;
import net.sourceforge.tess4j.Tesseract;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
public class FileService {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    public void extractAndSaveText(Long fileId, byte[] data) {
        try {
            String extractedText = "";
            Tika tika = new Tika();
            String mimeType = tika.detect(data);

            if (mimeType.startsWith("image/")) {
                Tesseract tesseract = new Tesseract();
                tesseract.setDatapath("D:/DocAutomation Project/BackEnd/tessdata");
                tesseract.setLanguage("eng");
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(data));
                if (bufferedImage != null) {
                    extractedText = tesseract.doOCR(bufferedImage);
                }
            } else {
                BodyContentHandler handler = new BodyContentHandler(-1);
                AutoDetectParser parser = new AutoDetectParser();
                Metadata metadata = new Metadata();
                ParseContext context = new ParseContext();
                try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
                    parser.parse(stream, handler, metadata, context);
                    extractedText = handler.toString();
                }
            }

            if (extractedText != null && !extractedText.trim().isEmpty()) {
                ExtractedContent contentEntity = extractedContentRepo.findByFileId(fileId)
                        .orElse(new ExtractedContent());
                contentEntity.setFileId(fileId);
                contentEntity.setContent(extractedText.trim());
                extractedContentRepo.save(contentEntity);
                System.out.println("Successfully extracted and saved for file ID: " + fileId);
            }
        } catch (Throwable t) {
            System.err.println("Extraction failed due to library error, but approval will continue: " + t.getMessage());
        }
    }
}