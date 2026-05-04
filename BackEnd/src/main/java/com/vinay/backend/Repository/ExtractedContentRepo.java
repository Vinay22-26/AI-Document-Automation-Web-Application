package com.vinay.backend.Repository;

import com.vinay.backend.Model.ExtractedContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExtractedContentRepo extends JpaRepository<ExtractedContent, Long> {
    Optional<ExtractedContent> findByFileId(Long fileId);
    
    List<ExtractedContent> findByEmail(String email); 
}