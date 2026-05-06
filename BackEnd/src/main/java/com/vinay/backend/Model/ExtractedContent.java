package com.vinay.backend.Model;

import jakarta.persistence.*;

@Entity
@Table(name = "extracted_content")
public class ExtractedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long fileId;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    private String email;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] originalBytes;

    @Column
    private String originalMimeType;

    // add getters/setters
    public byte[] getOriginalBytes() {
        return originalBytes;
    }

    public void setOriginalBytes(byte[] originalBytes) {
        this.originalBytes = originalBytes;
    }

    public String getOriginalMimeType() {
        return originalMimeType;
    }

    public void setOriginalMimeType(String originalMimeType) {
        this.originalMimeType = originalMimeType;
    }

    public ExtractedContent() {
    }

    public ExtractedContent(Long id, Long fileId, String content) {
        this.id = id;
        this.fileId = fileId;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}