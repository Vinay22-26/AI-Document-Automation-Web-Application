package com.vinay.backend.Controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vinay.backend.Model.AuditLogs;
import com.vinay.backend.Repository.AuditRepo;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "http://localhost:4200")
public class AuditLogController {

    @Autowired
    private AuditRepo auditRepo;

    @GetMapping()
    public ResponseEntity<?> auditLoginfo(@RequestParam("email") String email) {
        List<AuditLogs> data = auditRepo.findByEmail(email);
        if (!data.isEmpty()) {
            return ResponseEntity.ok(data);
        }
        return ResponseEntity.status(404).body("No audit logs found for email: " + email);
    }

    @GetMapping("/AdminLogs")
    public ResponseEntity<?> adminLogs(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: Admin role required");
        }

        List<AuditLogs> data = auditRepo.findAll();
        if (!data.isEmpty()) {
            return ResponseEntity.ok(data);
        }
        return ResponseEntity.status(404).body("No audit logs found");
    }
}