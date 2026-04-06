package com.vinay.backend.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "files_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class File {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String filePath;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] data;

    private String approval;

    private LocalDateTime processedAt = LocalDateTime.now();

    

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public byte[] getData() {
        return data;
    }

    public String getApproval() {
        return approval;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

   

    public void setId(Long id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setApproval(String approval) {
        this.approval = approval;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}