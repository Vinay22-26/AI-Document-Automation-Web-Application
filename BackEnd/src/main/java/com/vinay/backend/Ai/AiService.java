package com.vinay.backend.Ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.*;

@Service
public class AiService {

    @Value("${groq.api.key}")
    private String GROQ_API_KEY;

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    public String formatText(String rawText) {
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

            String prompt = """
                    You are a document extraction AI. Extract the content below and format it using markdown:
                    - Use ### for section headings
                    - Use **key:** value for labeled fields
                    - Use proper markdown tables (with | separators and header rows) for tabular data
                    - Do NOT add any commentary, explanations, or text outside the document content
                    - Do NOT add "Non-Invoice Text:" or any meta-commentary

                    Document text:
                    """ + rawText;

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            String[] models = {
                    "llama-4-maverick",
                    "llama-3.3-70b-versatile"

            };

            for (String model : models) {
                int attempt = 0;

                while (attempt < 2) {
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
                                Map.class);

                        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                            List choices = (List) response.getBody().get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                Map firstChoice = (Map) choices.get(0);
                                Map msg = (Map) firstChoice.get("message");

                                System.out.println("Provider Used: Groq");
                                System.out.println("Model Used: " + model);

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
                            Map.class);

                    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                        List choices = (List) response.getBody().get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map firstChoice = (Map) choices.get(0);
                            Map msg = (Map) firstChoice.get("message");

                            System.out.println("Provider Used: OpenAI");
                            System.out.println("Model Used: " + model);

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