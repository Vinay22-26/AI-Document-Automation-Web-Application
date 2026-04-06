package com.vinay.backend.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Data

@Table(name = "auditLogs")
public class AuditLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String filename;

    private String status;

    private String CreatedDate;

    private String ModifiedBy;

    private String Remarks;

    public void setTimestamp() {
        this.CreatedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFilename() {
        return filename;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedDate() {
        return CreatedDate;
    }

    public String getModifiedBy() {
        return ModifiedBy;
    }

    public String getRemarks() {
        return Remarks;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedDate(String createdDate) {
        this.CreatedDate = createdDate;
    }

    public void setModifiedBy(String modifiedBy) {
        this.ModifiedBy = modifiedBy;
    }

    public void setRemarks(String remarks) {
        this.Remarks = remarks;
    }
}