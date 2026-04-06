package com.vinay.backend.Repository;


import com.vinay.backend.Model.Register;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegisterRepo extends JpaRepository<Register, String> {

    Optional<Register> findByEmail(String email);
}