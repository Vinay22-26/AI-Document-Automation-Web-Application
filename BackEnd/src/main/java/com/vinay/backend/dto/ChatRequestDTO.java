package com.vinay.backend.dto;

public class ChatRequestDTO {
    private Long fileId;
    private String message;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}