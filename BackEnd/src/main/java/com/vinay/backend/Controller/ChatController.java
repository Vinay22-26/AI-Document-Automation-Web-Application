package com.vinay.backend.Controller;

import com.vinay.backend.Ai.ChatService;
import com.vinay.backend.Model.ChatMessage;
import com.vinay.backend.Repository.ChatMessageRepo;
import com.vinay.backend.dto.ChatRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepo chatMessageRepo;

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askQuestion(@RequestBody ChatRequestDTO request, HttpServletRequest httpRequest) {
        String email = (String) httpRequest.getAttribute("email");
        String aiResponse = chatService.getGroqResponse(request.getFileId(), request.getMessage(), email);
        
        Map<String, String> response = new HashMap<>();
        response.put("response", aiResponse);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{fileId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(@PathVariable Long fileId, HttpServletRequest httpRequest) {
        String email = (String) httpRequest.getAttribute("email");
        List<ChatMessage> history = chatMessageRepo.findByFileIdAndUserEmailOrderByTimestampAsc(fileId, email);
        return ResponseEntity.ok(history);
    }
}