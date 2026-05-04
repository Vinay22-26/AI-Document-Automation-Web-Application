package com.vinay.backend.Repository;

import com.vinay.backend.Model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepo extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByFileIdAndUserEmailOrderByTimestampAsc(Long fileId, String userEmail);
}