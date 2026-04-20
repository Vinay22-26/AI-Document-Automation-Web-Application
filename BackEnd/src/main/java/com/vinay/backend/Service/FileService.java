package com.vinay.backend.Service;

import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ExtractedContentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.*;

@Service
public class FileService {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @Value("${groq.api.key}")
    private String GROQ_API_KEY;

    public void extractAndSaveText(Long fileId, byte[] data) {
        try {
            System.out.println("Processing File ID: " + fileId);
            String mimeType = detectMimeType(data);
            String resultText = null;

            if (mimeType.contains("pdf") || mimeType.contains("word")) {
                String fileName = "file_" + fileId;
                String cloudinaryUrl = uploadToCloudinary(data, fileName); 
                System.out.println("Document detected. Using Cloudinary: " + cloudinaryUrl);
                
                resultText = callGroqWithUrl(cloudinaryUrl, "llama-3.3-70b-versatile");
            } else {
                System.out.println("Image detected. Using Groq Vision.");
                String base64 = Base64.getEncoder().encodeToString(data);
                resultText = callGroqVision(base64, mimeType);
            }

            if (resultText != null && !resultText.isEmpty()) {
                saveContent(fileId, resultText);
            }
        } catch (Exception e) {
            System.err.println("Process failed for ID " + fileId + ": " + e.getMessage());
        }
    }

    private String callGroqWithUrl(String fileUrl, String model) {
        try {
            RestTemplate restTemplate = createRestTemplate();
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", List.of(Map.of(
                "role", "user", 
                "content", "Analyze this document and extract all text content: " + fileUrl
            )));
            return postRequest(restTemplate, request);
        } catch (Exception e) {
            System.err.println("Groq URL Call Failed: " + e.getMessage());
            return null;
        }
    }

    private String callGroqVision(String base64, String mimeType) {
        String model = "meta-llama/llama-4-scout-17b-16e-instruct";
        try {
            RestTemplate restTemplate = createRestTemplate();
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "text", "text", "Extract all text from this image."),
                    Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mimeType + ";base64," + base64))
                )
            )));
            return postRequest(restTemplate, request);
        } catch (Exception e) {
            System.err.println("Groq Vision failed: " + e.getMessage());
            return null;
        }
    }

    private String postRequest(RestTemplate restTemplate, Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(GROQ_API_KEY);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "https://api.groq.com/openai/v1/chat/completions",
            new HttpEntity<>(request, headers),
            Map.class
        );
        
        List choices = (List) response.getBody().get("choices");
        return (String) ((Map) ((Map) choices.get(0)).get("message")).get("content");
    }

    private String detectMimeType(byte[] data) {
        if (data == null || data.length < 2) return "image/jpeg";
        if (data[0] == 0x25 && data[1] == 0x50) return "application/pdf";
        if (data[0] == 0x50 && data[1] == 0x4B) return "application/vnd.word";
        return "image/jpeg";
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }

    private void saveContent(Long fileId, String text) {
        ExtractedContent contentEntity = extractedContentRepo.findByFileId(fileId)
                .orElse(new ExtractedContent());
        contentEntity.setFileId(fileId);
        contentEntity.setContent(text.trim());
        extractedContentRepo.save(contentEntity);
        System.out.println("Saved successfully for ID: " + fileId);
    }

    private String uploadToCloudinary(byte[] data, String fileName) {
        // Implementation for Cloudinary SDK goes here
        return "https://res.cloudinary.com/ddhykw7qa/image/upload/" + fileName;
    }
}