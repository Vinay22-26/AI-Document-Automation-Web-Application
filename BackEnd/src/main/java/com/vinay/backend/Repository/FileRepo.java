package com.vinay.backend.Repository;


import com.vinay.backend.Model.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepo extends JpaRepository<File, String> {

    List<File> findByEmail(String email);


    Optional<File> findById(Long id);

    void deleteById(Long id);
}
