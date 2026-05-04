package com.vinay.backend.Ai;

import com.vinay.backend.Model.ChatMessage;
import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ChatMessageRepo;
import com.vinay.backend.Repository.ExtractedContentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @Autowired
    private ChatMessageRepo chatMessageRepo;

    private final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public String getGroqResponse(Long fileId, String userMessage, String userEmail) {
        String documentContext = "";
        
        if (fileId != null) {
            documentContext = extractedContentRepo.findByFileId(fileId)
                    .map(ExtractedContent::getContent)
                    .orElse("No document context found.");
            
            ChatMessage userChat = new ChatMessage();
            userChat.setFileId(fileId);
            userChat.setUserEmail(userEmail);
            userChat.setMessage(userMessage);
            userChat.setSender("user");
            userChat.setTimestamp(LocalDateTime.now());
            chatMessageRepo.save(userChat);
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(45000);
        RestTemplate restTemplate = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "llama-3.3-70b-versatile");
        
        List<Map<String, String>> messages = new ArrayList<>();
        
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are an AI assistant analyzing documents. Use the provided document context to answer the user's prompt accurately. \n\nDocument Context:\n" + documentContext);
        messages.add(systemMessage);

        Map<String, String> userPrompt = new HashMap<>();
        userPrompt.put("role", "user");
        userPrompt.put("content", userMessage);
        messages.add(userPrompt);

        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String aiResponseText = "Failed to generate response.";

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(GROQ_API_URL, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                Map<String, Object> firstChoice = choices.get(0);
                Map<String, String> message = (Map<String, String>) firstChoice.get("message");
                aiResponseText = message.get("content");
            }
        } catch (Exception e) {
            aiResponseText = "Error communicating with Groq API: " + e.getMessage();
        }

        if (fileId != null) {
            ChatMessage aiChat = new ChatMessage();
            aiChat.setFileId(fileId);
            aiChat.setUserEmail(userEmail);
            aiChat.setMessage(aiResponseText);
            aiChat.setSender("ai");
            aiChat.setTimestamp(LocalDateTime.now());
            chatMessageRepo.save(aiChat);
        }
        
        return aiResponseText;
    }
}