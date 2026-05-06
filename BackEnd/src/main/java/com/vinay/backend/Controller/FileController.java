package com.vinay.backend.Controller;

import com.vinay.backend.Model.AuditLogs;
import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Model.File;
import com.vinay.backend.Repository.AuditRepo;
import com.vinay.backend.Repository.FileRepo;
import com.vinay.backend.Service.FileService;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ExtractedContentRepo;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:4200")
public class FileController {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @Autowired
    private FileRepo fileRepo;

    @Autowired
    private AuditRepo auditRepo;

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<?> fileUploading(
            @RequestParam("file") MultipartFile file,
            @RequestParam("email") String email,
            @RequestParam(value = "approval", required = false) String approval) throws IOException {

        String originalName = file.getOriginalFilename();
        List<File> userFiles = fileRepo.findByEmail(email);
        Optional<File> existingFile = userFiles.stream()
                .filter(f -> f.getFileName().equals(originalName))
                .findFirst();

        File entity = existingFile.orElse(new File());
        entity.setFileName(originalName);
        entity.setFilePath(originalName);
        entity.setData(file.getBytes());
        entity.setApproval(approval != null ? approval : "pending");
        entity.setEmail(email);

        File saved = fileRepo.save(entity);

        AuditLogs log = new AuditLogs();
        log.setFilename(originalName);
        log.setEmail(email);
        log.setTimestamp();

        if (existingFile.isPresent()) {
            log.setStatus("UPDATED");
            log.setModifiedBy(email);
            log.setRemarks("Modified existing file content for: " + originalName);
        } else {
            log.setStatus("UPLOADED ");
            log.setModifiedBy("New File");
            log.setRemarks("Uploaded new file: " + originalName);
        }

        auditRepo.save(log);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/AllFiles")
    public ResponseEntity<?> allFiles(@RequestParam String email) {
        List<File> data = fileRepo.findByEmail(email);
        if (!data.isEmpty()) {
            return ResponseEntity.ok(data);
        }
        return ResponseEntity.status(404).body("No files found for this email");
    }

    @GetMapping("/AllFiles/{id}")
    public ResponseEntity<?> filesinfo(@PathVariable Long id) {
        Optional<File> fileData = fileRepo.findById(id);

        if (fileData.isPresent()) {
            File file = fileData.get();
            String fileName = file.getFileName().toLowerCase();
            byte[] data = file.getData();
            String contentType = "application/octet-stream";

            try {
                if (fileName.endsWith(".docx")) {
                    ByteArrayInputStream docIn = new ByteArrayInputStream(data);
                    XWPFDocument document = new XWPFDocument(docIn);
                    ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
                    PdfConverter.getInstance().convert(document, pdfOut, PdfOptions.create());
                    data = pdfOut.toByteArray();
                    contentType = "application/pdf";
                    fileName = fileName.replace(".docx", ".pdf");
                } else if (fileName.endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (fileName.endsWith(".png")) {
                    contentType = "image/png";
                } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                }

                return ResponseEntity.ok()
                        .header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
                        .header("Content-Type", contentType)
                        .body(data);

            } catch (Exception e) {
                return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
            }
        }
        return ResponseEntity.status(404).body("File not found with id: " + id);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id, @RequestParam("email") String email) {
        Optional<File> optionalFile = fileRepo.findById(id);
        if (optionalFile.isEmpty()) {
            return ResponseEntity.status(404).body("File not found with id: " + id);
        }
        File file = optionalFile.get();
        String fileName = file.getFileName();

        fileRepo.delete(file);

        AuditLogs log = new AuditLogs();
        log.setFilename(fileName);
        log.setEmail(email);
        log.setStatus("DELETED");
        log.setModifiedBy(email);
        log.setRemarks("Deleted file: " + fileName);
        log.setTimestamp();

        auditRepo.save(log);
        return ResponseEntity.ok("File deleted successfully");
    }

    @PutMapping("/approve/{id}")
    public ResponseEntity<?> approveFile(@PathVariable Long id,
            @RequestParam("email") String email,
            HttpServletRequest request) {

        Object roleAttr = request.getAttribute("role");
        String role = (roleAttr != null) ? roleAttr.toString() : "";

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only Admins can approve files. Current role: " + role);
        }

        Optional<File> optionalFile = fileRepo.findById(id);
        if (optionalFile.isEmpty()) {
            return ResponseEntity.status(404).body("File not found");
        }

        File file = optionalFile.get();
        file.setApproval("approved");
        fileRepo.save(file);

        try {
            fileService.extractAndSaveText(file.getId(), file.getData(), file.getEmail());
        } catch (Exception e) {
            System.err.println("Extraction failed but file was approved: " + e.getMessage());
        }

        AuditLogs log = new AuditLogs();
        log.setFilename(file.getFileName());
        log.setEmail(email);
        log.setStatus("APPROVED");
        log.setModifiedBy(email);
        log.setRemarks("Approved file: " + file.getFileName());
        log.setTimestamp();

        auditRepo.save(log);
        return ResponseEntity.ok("File approved successfully");
    }

    @GetMapping("/AdminFiles")
    public ResponseEntity<?> adminFiles(HttpServletRequest request) {
        Object roleAttr = request.getAttribute("role");
        String role = (roleAttr != null) ? roleAttr.toString() : "";

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Admin only");
        }

        List<File> data = fileRepo.findAll();
        if (!data.isEmpty()) {
            return ResponseEntity.ok(data);
        }
        return ResponseEntity.status(404).body("No files found");
    }

    // Export edited text as PDF and download
    @GetMapping("/export/pdf/{fileId}")
    public ResponseEntity<?> exportAsPdf(@PathVariable Long fileId) {
        Optional<ExtractedContent> optContent = extractedContentRepo.findByFileId(fileId);
        if (optContent.isEmpty())
            return ResponseEntity.notFound().build();

        try {
            String text = optContent.get().getContent();
            PDDocument doc = new PDDocument();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDPageContentStream stream = new PDPageContentStream(doc, page);
            stream.setFont(PDType1Font.HELVETICA, 11);
            stream.setLeading(16f);
            stream.beginText();
            stream.newLineAtOffset(50, 780);

            // Word-wrap lines to fit A4 width
            for (String line : text.split("\n")) {
                List<String> wrapped = wrapLine(line, 95);
                for (String wl : wrapped) {
                    stream.showText(wl.replaceAll("[^\\x20-\\x7E]", ""));
                    stream.newLine();
                }
            }
            stream.endText();
            stream.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            doc.close();

            Optional<File> fileOpt = fileRepo.findById(fileId);
            String name = fileOpt.map(f -> f.getFileName()
                    .replaceAll("\\.(pdf|docx|png|jpg|jpeg)$", "") + "_edited.pdf")
                    .orElse("export.pdf");

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                    .header("Content-Type", "application/pdf")
                    .body(out.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.status(500).body("PDF generation failed: " + e.getMessage());
        }
    }

    private List<String> wrapLine(String line, int maxChars) {
        List<String> result = new ArrayList<>();
        while (line.length() > maxChars) {
            int cut = line.lastIndexOf(' ', maxChars);
            if (cut == -1)
                cut = maxChars;
            result.add(line.substring(0, cut));
            line = line.substring(cut).stripLeading();
        }
        result.add(line);
        return result;
    }
}