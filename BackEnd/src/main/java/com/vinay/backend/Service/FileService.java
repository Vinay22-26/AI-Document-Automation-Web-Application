package com.vinay.backend.Service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;

@Service
public class FileService {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @Value("${groq.api.key}")
    private String GROQ_API_KEY;

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    public void extractAndSaveText(Long fileId, byte[] data) {
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
                String formattedText = formatText(rawExtractedText);

                ExtractedContent contentEntity = extractedContentRepo.findByFileId(fileId)
                        .orElse(new ExtractedContent());
                contentEntity.setFileId(fileId);
                contentEntity.setContent(formattedText.trim());
                extractedContentRepo.save(contentEntity);
                System.out.println("Successfully processed file ID: " + fileId);
            }
        } catch (Throwable t) {
            System.err.println("Process failed: " + t.getMessage());
        }
    }

    private String extractFromPDF(byte[] data) {
        StringBuilder result = new StringBuilder();
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(data))) {
            PDFRenderer renderer = new PDFRenderer(document);
            Tesseract tesseract = getTesseract();

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
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
            if (image == null) return "";
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
        if (text == null) return "";
        return text.replaceAll("[\\t\\n\\r]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String formatText(String rawText) {
        String result = callGroq(rawText);

        if (result == null || result.isEmpty()) {
            result = callChatGPT(rawText);
        }

        if (result == null || result.isEmpty()) {
            return rawText;
        }

        return result;
    }

    private String callGroq(String rawText) {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(20000);

            RestTemplate restTemplate = new RestTemplate(factory);

            String prompt = "Extract complete invoice data with correct table structure. Do not miss any row or column. Preserve totals accurately. Fix OCR issues.\n\n" + rawText;

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            String[] models = {
                    "llama-4-maverick",
                    "llama-3.3-70b-versatile",
                    "qwen-3-32b"
            };

            for (String model : models) {
                int attempt = 0;
                System.out.println("Using Groq with model: " + model);

                while (attempt < 3) {
                    try {
                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("model", model);
                        requestBody.put("messages", List.of(message));

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setBearerAuth(GROQ_API_KEY);

                        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                        ResponseEntity<Map> response = restTemplate.postForEntity(
                                "https://api.groq.com/openai/v1/chat/completions",
                                entity,
                                Map.class
                        );

                        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                            List choices = (List) response.getBody().get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                Map firstChoice = (Map) choices.get(0);
                                Map msg = (Map) firstChoice.get("message");
                                System.out.println("Groq success with model: " + model);
                                return (String) msg.get("content");
                            }
                        }

                    } catch (Exception e) {
                        String err = e.getMessage() != null ? e.getMessage() : "";

                        if (err.contains("context_length_exceeded")) {
                            rawText = smartTrim(rawText);
                            message.put("content", prompt + rawText);
                            attempt = 0;
                        } else if (err.contains("429") || err.contains("503")) {
                            sleep(attempt);
                        } else {
                            break;
                        }
                    }
                    attempt++;
                }
            }
        } catch (Exception e) {
            System.err.println("Groq API Error: " + e.getMessage());
        }
        return null;
    }

    private String callChatGPT(String rawText) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String prompt = "Extract full invoice with all rows, columns, totals. No data loss.\n\n" + rawText;

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            String[] models = {
                    "gpt-5.3-instant",
                    "gpt-5.4-mini"
            };

            for (String model : models) {
                try {
                    System.out.println("Using ChatGPT with model: " + model);

                    Map<String, Object> request = new HashMap<>();
                    request.put("model", model);
                    request.put("messages", List.of(message));

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(OPENAI_API_KEY);

                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

                    ResponseEntity<Map> response = restTemplate.postForEntity(
                            "https://api.openai.com/v1/chat/completions",
                            entity,
                            Map.class
                    );

                    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                        List choices = (List) response.getBody().get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map firstChoice = (Map) choices.get(0);
                            Map msg = (Map) firstChoice.get("message");
                            System.out.println("ChatGPT success with model: " + model);
                            return (String) msg.get("content");
                        }
                    }

                } catch (Exception e) {
                    String err = e.getMessage() != null ? e.getMessage() : "";

                    if (err.contains("context_length_exceeded")) {
                        rawText = smartTrim(rawText);
                        message.put("content", prompt + rawText);
                    } else if (err.contains("429") || err.contains("503")) {
                        Thread.sleep(2000);
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ChatGPT Error: " + e.getMessage());
        }
        return null;
    }

    private String smartTrim(String text) {
        int keepStart = (int) (text.length() * 0.5);
        int keepEnd = (int) (text.length() * 0.3);
        return text.substring(0, keepStart) + text.substring(text.length() - keepEnd);
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(2000 * (attempt + 1));
        } catch (InterruptedException ignored) {
        }
    }
}