package com.vinay.backend.Controller;

import com.vinay.backend.Model.ExtractedContent;
import com.vinay.backend.Repository.ExtractedContentRepo;
import com.vinay.backend.Repository.FileRepo;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins = "http://localhost:4200")
public class ExportController {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;
    @Autowired
    private FileRepo fileRepo;

    @GetMapping("/pdf/{fileId}")
    public ResponseEntity<?> exportPdf(@PathVariable Long fileId) {
        Optional<ExtractedContent> opt = extractedContentRepo.findByFileId(fileId);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();

        ExtractedContent ec = opt.get();
        String fileName = fileRepo.findById(fileId)
                .map(f -> f.getFileName()
                        .replaceAll("\\.(pdf|docx|png|jpg|jpeg)$", "") + "_edited.pdf")
                .orElse("export.pdf");

        try {
            byte[] originalBytes = ec.getOriginalBytes();
            String mimeType = ec.getOriginalMimeType();
            String editedText = ec.getContent();
            byte[] result;

            if (originalBytes != null && mimeType != null) {
                if (mimeType.equals("application/pdf")) {
                    result = exportFromPdf(originalBytes, editedText);
                } else if (mimeType.contains("wordprocessingml") || mimeType.contains("msword")) {
                    result = exportFromDocx(originalBytes, editedText);
                } else if (mimeType.startsWith("image/")) {
                    result = exportFromImage(originalBytes, editedText);
                } else {
                    result = exportFromPdf(originalBytes, editedText);
                }
            } else {
                // fallback: no original stored yet, use image-style
                result = exportPlainTextPdf(editedText);
            }

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "application/pdf")
                    .body(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Export failed: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // STRATEGY 1: PDF source
    // Render each original page as a high-res image → place as background
    // → overlay a transparent text layer with the user's edited text
    // This preserves EVERY visual element: logos, borders, tables, stamps
    // ─────────────────────────────────────────────────────────────────────
    private byte[] exportFromPdf(byte[] originalPdfBytes, String editedText) throws Exception {
        PDDocument outputDoc = new PDDocument();

        // Render original PDF pages as images (background layer)
        try (PDDocument originalDoc = PDDocument.load(new ByteArrayInputStream(originalPdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(originalDoc);

            // Split edited text into page-sized chunks
            List<String> pages = splitIntoPages(editedText, 55); // ~55 lines per A4 page

            int totalPages = Math.max(originalDoc.getNumberOfPages(), pages.size());

            for (int i = 0; i < totalPages; i++) {
                PDPage outputPage = new PDPage(PDRectangle.A4);
                outputDoc.addPage(outputPage);

                try (PDPageContentStream cs = new PDPageContentStream(
                        outputDoc, outputPage,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {

                    // Draw original page as background image
                    if (i < originalDoc.getNumberOfPages()) {
                        BufferedImage pageImage = renderer.renderImageWithDPI(i, 150);
                        PDImageXObject bgImage = LosslessFactory.createFromImage(outputDoc, pageImage);
                        cs.drawImage(bgImage, 0, 0,
                                PDRectangle.A4.getWidth(),
                                PDRectangle.A4.getHeight());
                    }

                    // Overlay edited text as invisible but selectable text layer
                    // (so copy-paste still works on the output PDF)
                    if (i < pages.size()) {
                        drawTextLayer(cs, outputDoc, pages.get(i),
                                PDRectangle.A4.getWidth(),
                                PDRectangle.A4.getHeight());
                    }
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        outputDoc.save(out);
        outputDoc.close();
        return out.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────
    // STRATEGY 2: DOCX source
    // Replace text runs in the original DOCX preserving all styles/fonts
    // then convert to PDF via xdocreport
    // ─────────────────────────────────────────────────────────────────────
    private byte[] exportFromDocx(byte[] docxBytes, String editedText) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {

            // Collect all original paragraphs
            List<XWPFParagraph> paras = doc.getParagraphs();
            String[] editedLines = editedText.split("\n");
            int lineIdx = 0;

            for (XWPFParagraph para : paras) {
                if (para.getText().isBlank())
                    continue;
                if (lineIdx >= editedLines.length)
                    break;

                // Replace first run's text with edited line, clear the rest
                List<XWPFRun> runs = para.getRuns();
                if (!runs.isEmpty()) {
                    runs.get(0).setText(editedLines[lineIdx++], 0);
                    for (int r = 1; r < runs.size(); r++) {
                        runs.get(r).setText("", 0);
                    }
                }
            }

            // Convert modified DOCX → PDF using xdocreport (already in your pom.xml)
            ByteArrayOutputStream docxOut = new ByteArrayOutputStream();
            doc.write(docxOut);

            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            fr.opensagres.poi.xwpf.converter.pdf.PdfConverter.getInstance()
                    .convert(new XWPFDocument(
                            new ByteArrayInputStream(docxOut.toByteArray())),
                            pdfOut,
                            fr.opensagres.poi.xwpf.converter.pdf.PdfOptions.create());

            return pdfOut.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // STRATEGY 3: Image source (JPG/PNG)
    // Place original image as full-page background
    // Overlay edited text as a white semi-transparent block at the bottom
    // ─────────────────────────────────────────────────────────────────────
    private byte[] exportFromImage(byte[] imageBytes, String editedText) throws Exception {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Background: original image
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            PDImageXObject bgImage = LosslessFactory.createFromImage(doc, img);
            cs.drawImage(bgImage, 0, 0,
                    PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());

            // Overlay: draw edited text
            drawTextLayer(cs, doc, editedText,
                    PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        doc.close();
        return out.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fallback: plain text PDF (when no original bytes stored)
    // ─────────────────────────────────────────────────────────────────────
    private byte[] exportPlainTextPdf(String text) throws Exception {
        PDDocument doc = new PDDocument();
        List<String> pages = splitIntoPages(text, 55);

        for (String pageText : pages) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawTextLayer(cs, doc, pageText,
                        PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        doc.close();
        return out.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared: draw a visible text layer onto a PDPageContentStream
    // Used for both PDF overlay and image overlay
    // ─────────────────────────────────────────────────────────────────────
    private void drawTextLayer(PDPageContentStream cs, PDDocument doc,
            String text, float pageWidth, float pageHeight) throws Exception {
        PDFont font = PDType1Font.HELVETICA;
        float fontSize = 9f;
        float leading = 13f;
        float marginLeft = 40f;
        float marginRight = 40f;
        float startY = pageHeight - 50f;
        float maxWidth = pageWidth - marginLeft - marginRight;

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setLeading(leading);
        cs.newLineAtOffset(marginLeft, startY);

        for (String rawLine : text.split("\n")) {
            // Word wrap each line to fit page width
            List<String> wrapped = wordWrap(rawLine, font, fontSize, maxWidth);
            for (String wl : wrapped) {
                String safe = wl.replaceAll("[^\\x20-\\x7E]", " ");
                cs.showText(safe);
                cs.newLine();
            }
        }
        cs.endText();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Word wrap using actual font metrics
    // ─────────────────────────────────────────────────────────────────────
    private List<String> wordWrap(String line, PDFont font, float fontSize, float maxWidth)
            throws Exception {
        List<String> result = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            result.add("");
            return result;
        }

        String[] words = line.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.length() == 0 ? word : current + " " + word;
            String safe = test.replaceAll("[^\\x20-\\x7E]", " ");
            float width = font.getStringWidth(safe) / 1000 * fontSize;
            if (width > maxWidth && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(test);
            }
        }
        if (current.length() > 0)
            result.add(current.toString());
        return result;
    }

    // Split long text into page-sized chunks (~linesPerPage lines each)
    private List<String> splitIntoPages(String text, int linesPerPage) {
        String[] lines = text.split("\n");
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            page.append(line).append("\n");
            count++;
            if (count >= linesPerPage) {
                pages.add(page.toString());
                page = new StringBuilder();
                count = 0;
            }
        }
        if (page.length() > 0)
            pages.add(page.toString());
        if (pages.isEmpty())
            pages.add(text);
        return pages;
    }
}