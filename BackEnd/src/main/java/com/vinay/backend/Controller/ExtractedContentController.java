package com.vinay.backend.Controller;

import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ExtractedContentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/extract")
@CrossOrigin(origins = "http://localhost:4200")
public class ExtractedContentController {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @GetMapping("/all")
    public ResponseEntity<List<ExtractedContent>> getAllExtractedContent() {
        return ResponseEntity.ok(extractedContentRepo.findAll());
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<?> getContentByFileId(@PathVariable Long fileId) {
        return extractedContentRepo.findByFileId(fileId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}