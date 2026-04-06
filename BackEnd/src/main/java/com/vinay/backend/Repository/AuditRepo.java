package com.vinay.backend.Repository;


import com.vinay.backend.Model.AuditLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditRepo extends JpaRepository<AuditLogs, Long> {
    List<AuditLogs> findByEmail(String email);
}
