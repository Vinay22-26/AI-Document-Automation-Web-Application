package com.vinay.backend.Controller;

import com.vinay.backend.Model.Login;
import com.vinay.backend.Model.Register;
import com.vinay.backend.Repository.RegisterRepo;
import com.vinay.backend.Security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class RegisterNLoginController {

    @Autowired
    private RegisterRepo register;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Register request) {
        if (register.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body("Email already exists!");
        }
        register.save(request);
        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Login loginRequest) {
        if (loginRequest == null || loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.status(400).body("Invalid request: Missing credentials");
        }

        if ("admin123@gmail.com".equals(loginRequest.getEmail()) && "admin123".equals(loginRequest.getPassword())) {
            String token = jwtUtil.generateToken(loginRequest.getEmail(), "ADMIN");
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            response.put("role", "ADMIN");
            response.put("email", loginRequest.getEmail());
            return ResponseEntity.ok(response);
        }

        Optional<Register> userOptional = register.findByEmail(loginRequest.getEmail());
        if (userOptional.isPresent()) {
            Register user = userOptional.get();
            String storedPassword = user.getPassword();
            
            if (storedPassword != null && storedPassword.equals(loginRequest.getPassword())) {
                String token = jwtUtil.generateToken(user.getEmail(), "USER");
                Map<String, String> response = new HashMap<>();
                response.put("token", token);
                response.put("role", "USER");
                response.put("email", user.getEmail());
                return ResponseEntity.ok(response);
            }
        }
        return ResponseEntity.status(401).body("Invalid Email or Password");
    }

    @GetMapping("/Profile")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        String email = (String) request.getAttribute("email");
        String role = (String) request.getAttribute("role");

        if (email == null) {
            return ResponseEntity.status(401).body("Unauthorized: No email found in token");
        }
        Optional<Register> userOptional = register.findByEmail(email);
        if (userOptional.isPresent()) {
            Register user = userOptional.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("user_name", user.getUserName());
            response.put("email", user.getEmail());
            response.put("gender", user.getGender());
            response.put("phone_number", user.getPhoneNumber());
            response.put("role", role != null ? role : "USER");

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(404).body("User not found");
    }
}