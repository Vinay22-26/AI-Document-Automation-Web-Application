package com.vinay.backend.dto;

public class ExtractedContentDTO {
    private Long fileId;
    private String fileName;
    private String content;

    public ExtractedContentDTO(Long fileId, String fileName, String content) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.content = content;
    }

    public Long getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getContent() { return content; }
}