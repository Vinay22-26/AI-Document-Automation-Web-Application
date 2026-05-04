package com.vinay.backend.Controller;

import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ExtractedContentRepo;
import com.vinay.backend.Repository.FileRepo;
import com.vinay.backend.dto.ExtractedContentDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/extract")
@CrossOrigin(origins = "http://localhost:4200")
public class ExtractedContentController {

    @Autowired
    private FileRepo fileRepo;

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @GetMapping("/all")
    public ResponseEntity<List<ExtractedContentDTO>> getAllExtractedContent() {

        List<ExtractedContent> extractedList = extractedContentRepo.findAll();

        List<ExtractedContentDTO> result = extractedList.stream().map(item -> {
            String fileName = fileRepo.findById(item.getFileId())
                    .map(file -> file.getFileName())
                    .orElse("Unknown File");

            return new ExtractedContentDTO(
                    item.getFileId(),
                    fileName,
                    item.getContent());
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/user/{email}")
    public ResponseEntity<List<ExtractedContentDTO>> getContentByEmail(@PathVariable String email) {

        List<ExtractedContent> extractedList = extractedContentRepo.findByEmail(email);

        List<ExtractedContentDTO> result = extractedList.stream().map(item -> {
            String fileName = fileRepo.findById(item.getFileId())
                    .map(file -> file.getFileName())
                    .orElse("Unknown File");

            return new ExtractedContentDTO(
                    item.getFileId(),
                    fileName,
                    item.getContent());
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<?> getContentByFileId(@PathVariable Long fileId) {
        return extractedContentRepo.findByFileId(fileId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/DeletingExtractedContent/{fileId}")
    public ResponseEntity<?> deleteExtractedContent(@PathVariable Long fileId) {
        return extractedContentRepo.findByFileId(fileId)
                .map(content -> {
                    extractedContentRepo.delete(content);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}