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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins="http://localhost:4200")
public class ExportController {

    @Autowired
    private ExtractedContentRepo extractedContentRepo;

    @Autowired
    private FileRepo fileRepo;

    private static final float PAGE_WIDTH=PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT=PDRectangle.A4.getHeight();
    private static final float MARGIN_LEFT=45f;
    private static final float MARGIN_RIGHT=45f;
    private static final float MARGIN_TOP=50f;
    private static final float MARGIN_BOTTOM=45f;
    private static final float CONTENT_WIDTH=PAGE_WIDTH-MARGIN_LEFT-MARGIN_RIGHT;

    private static final float FONT_SIZE_H1=16f;
    private static final float FONT_SIZE_H2=14f;
    private static final float FONT_SIZE_H3=12f;
    private static final float FONT_SIZE_NORMAL=10f;
    private static final float FONT_SIZE_TABLE=9f;
    private static final float LINE_HEIGHT_NORMAL=15f;
    private static final float LINE_HEIGHT_TABLE=13f;

    @GetMapping("/pdf/{fileId}")
    public ResponseEntity<?> exportPdf(@PathVariable Long fileId){
        Optional<ExtractedContent> opt=extractedContentRepo.findByFileId(fileId);

        if(opt.isEmpty())
            return ResponseEntity.notFound().build();

        ExtractedContent ec=opt.get();

        String fileName=fileRepo.findById(fileId)
                .map(f->f.getFileName()
                        .replaceAll("\\.(pdf|docx|png|jpg|jpeg)$","")+"_edited.pdf")
                .orElse("export.pdf");

        try{
            byte[] originalBytes=ec.getOriginalBytes();
            String mimeType=ec.getOriginalMimeType();
            String editedText=ec.getContent();
            byte[] result;

            if(originalBytes!=null&&mimeType!=null){
                if(mimeType.equals("application/pdf")){
                    result=exportFromPdf(originalBytes,editedText);
                }else if(mimeType.startsWith("image/")){
                    result=exportFromImage(originalBytes,editedText);
                }else{
                    result=renderMarkdownAsPdf(editedText);
                }
            }else{
                result=renderMarkdownAsPdf(editedText);
            }

            return ResponseEntity.ok()
                    .header("Content-Disposition","attachment; filename=\""+fileName+"\"")
                    .header("Content-Type","application/pdf")
                    .body(result);

        }catch(Exception e){
            return ResponseEntity.status(500).body("Export failed: "+e.getMessage());
        }
    }

    private byte[] exportFromPdf(byte[] originalPdfBytes,String editedText)throws Exception{
        PDDocument outputDoc=new PDDocument();

        try(PDDocument originalDoc=PDDocument.load(new ByteArrayInputStream(originalPdfBytes))){
            PDFRenderer renderer=new PDFRenderer(originalDoc);
            List<List<LineToken>> pages=paginateMarkdown(editedText);
            int totalPages=Math.max(originalDoc.getNumberOfPages(),pages.size());

            for(int i=0;i<totalPages;i++){
                PDPage outputPage=new PDPage(PDRectangle.A4);
                outputDoc.addPage(outputPage);

                if(i<originalDoc.getNumberOfPages()){
                    try(PDPageContentStream bg=new PDPageContentStream(
                            outputDoc,outputPage,
                            PDPageContentStream.AppendMode.APPEND,true,true)){
                        BufferedImage pageImage=renderer.renderImageWithDPI(i,150);
                        PDImageXObject bgImg=LosslessFactory.createFromImage(outputDoc,pageImage);
                        bg.drawImage(bgImg,0,0,PAGE_WIDTH,PAGE_HEIGHT);
                    }
                }

                if(i<pages.size()){
                    try(PDPageContentStream cs=new PDPageContentStream(
                            outputDoc,outputPage,
                            PDPageContentStream.AppendMode.APPEND,true,true)){
                        drawRenderedPage(cs,outputDoc,pages.get(i));
                    }
                }
            }
        }

        ByteArrayOutputStream out=new ByteArrayOutputStream();
        outputDoc.save(out);
        outputDoc.close();
        return out.toByteArray();
    }

    private byte[] renderMarkdownAsPdf(String editedText)throws Exception{
        PDDocument doc=new PDDocument();
        List<List<LineToken>> pages=paginateMarkdown(editedText);

        for(List<LineToken> pageTokens:pages){
            PDPage page=new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try(PDPageContentStream cs=new PDPageContentStream(doc,page)){
                cs.setNonStrokingColor(1f,1f,1f);
                cs.addRect(0,0,PAGE_WIDTH,PAGE_HEIGHT);
                cs.fill();
                drawRenderedPage(cs,doc,pageTokens);
            }
        }

        ByteArrayOutputStream out=new ByteArrayOutputStream();
        doc.save(out);
        doc.close();
        return out.toByteArray();
    }

    private byte[] exportFromImage(byte[] imageBytes,String editedText)throws Exception{
        PDDocument doc=new PDDocument();
        PDPage page=new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try(PDPageContentStream cs=new PDPageContentStream(doc,page)){
            BufferedImage img=ImageIO.read(new ByteArrayInputStream(imageBytes));
            PDImageXObject bgImage=LosslessFactory.createFromImage(doc,img);
            cs.drawImage(bgImage,0,0,PAGE_WIDTH,PAGE_HEIGHT);

            cs.setNonStrokingColor(1f,1f,1f);
            cs.addRect(MARGIN_LEFT-5,MARGIN_BOTTOM-5,
                    CONTENT_WIDTH+10,PAGE_HEIGHT-MARGIN_TOP-MARGIN_BOTTOM+10);
            cs.fill();
        }

        List<List<LineToken>> pages=paginateMarkdown(editedText);

        if(!pages.isEmpty()){
            try(PDPageContentStream cs=new PDPageContentStream(
                    doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){
                drawRenderedPage(cs,doc,pages.get(0));
            }
        }

        ByteArrayOutputStream out=new ByteArrayOutputStream();
        doc.save(out);
        doc.close();
        return out.toByteArray();
    }

    private enum TokenType{
        H1,H2,H3,
        BOLD_LABEL,
        NORMAL,
        TABLE_HEADER,
        TABLE_SEPARATOR,
        TABLE_ROW,
        DIVIDER,
        BLANK
    }

    private static class LineToken{
        TokenType type;
        String text;
        String[] cells;
        boolean isTableHeader;

        LineToken(TokenType t,String text){
            this.type=t;
            this.text=text;
        }

        LineToken(TokenType t,String[] cells,boolean header){
            this.type=t;
            this.cells=cells;
            this.isTableHeader=header;
        }
    }

    private List<List<LineToken>> paginateMarkdown(String markdown){
        List<LineToken> allTokens=parseMarkdown(markdown);
        List<List<LineToken>> pages=new ArrayList<>();
        List<LineToken> current=new ArrayList<>();
        float usedY=0f;
        float maxY=PAGE_HEIGHT-MARGIN_TOP-MARGIN_BOTTOM;

        for(LineToken t:allTokens){
            float h=estimateTokenHeight(t);

            if(usedY+h>maxY&&!current.isEmpty()){
                pages.add(current);
                current=new ArrayList<>();
                usedY=0f;
            }

            current.add(t);
            usedY+=h;
        }

        if(!current.isEmpty())pages.add(current);
        if(pages.isEmpty())pages.add(new ArrayList<>());
        return pages;
    }

    private float estimateTokenHeight(LineToken t){
        switch(t.type){
            case H1:return LINE_HEIGHT_NORMAL*2.2f;
            case H2:return LINE_HEIGHT_NORMAL*2.0f;
            case H3:return LINE_HEIGHT_NORMAL*1.8f;
            case DIVIDER:return 10f;
            case BLANK:return LINE_HEIGHT_NORMAL*0.6f;
            case TABLE_HEADER:
            case TABLE_ROW:return LINE_HEIGHT_TABLE+6f;
            case TABLE_SEPARATOR:return 0f;
            default:
                int chars=t.text!=null?t.text.length():40;
                int charsPerLine=(int)(CONTENT_WIDTH/(FONT_SIZE_NORMAL*0.55f));
                int lines=Math.max(1,(int)Math.ceil((double)chars/charsPerLine));
                return LINE_HEIGHT_NORMAL*lines;
        }
    }

    private List<LineToken> parseMarkdown(String markdown){
        List<LineToken> tokens=new ArrayList<>();

        if(markdown==null||markdown.isBlank())return tokens;

        String[] lines=markdown.split("\n");
        int i=0;

        while(i<lines.length){
            String raw=lines[i];
            String line=raw.trim();

            if(line.isEmpty()){
                tokens.add(new LineToken(TokenType.BLANK,""));
                i++;
                continue;
            }

            if(line.startsWith("### ")){
                tokens.add(new LineToken(TokenType.H3,line.substring(4).trim()));
                i++;
                continue;
            }

            if(line.startsWith("## ")){
                tokens.add(new LineToken(TokenType.H2,line.substring(3).trim()));
                i++;
                continue;
            }

            if(line.startsWith("# ")){
                tokens.add(new LineToken(TokenType.H1,line.substring(2).trim()));
                i++;
                continue;
            }

            if(line.matches("^[-*_]{3,}$")){
                tokens.add(new LineToken(TokenType.DIVIDER,""));
                i++;
                continue;
            }

            if(line.startsWith("|")){
                boolean isHeader=false;

                if(i+1<lines.length){
                    String nextLine=lines[i+1].trim();

                    if(nextLine.startsWith("|")&&nextLine.contains("---")){
                        isHeader=true;
                    }
                }

                if(line.contains("---")&&line.replace("|","").replace("-","").replace(":","").trim().isEmpty()){
                    tokens.add(new LineToken(TokenType.TABLE_SEPARATOR,""));
                    i++;
                    continue;
                }

                String[] cells=parseTableRow(line);
                TokenType ttype=isHeader?TokenType.TABLE_HEADER:TokenType.TABLE_ROW;
                tokens.add(new LineToken(ttype,cells,isHeader));
                i++;
                continue;
            }

            if(line.startsWith("**")&&line.contains(":**")){
                tokens.add(new LineToken(TokenType.BOLD_LABEL,line));
                i++;
                continue;
            }

            if(line.startsWith("- ")||line.startsWith("* ")){
                String content=line.substring(2).trim();

                if(content.startsWith("**")&&content.contains(":**")){
                    tokens.add(new LineToken(TokenType.BOLD_LABEL,content));
                }else{
                    tokens.add(new LineToken(TokenType.NORMAL,"• "+content));
                }

                i++;
                continue;
            }

            tokens.add(new LineToken(TokenType.NORMAL,line));
            i++;
        }

        return tokens;
    }

    private String[] parseTableRow(String line){
        String stripped=line.trim();

        if(stripped.startsWith("|"))stripped=stripped.substring(1);
        if(stripped.endsWith("|"))stripped=stripped.substring(0,stripped.length()-1);

        String[] parts=stripped.split("\\|");
        String[] cells=new String[parts.length];

        for(int j=0;j<parts.length;j++){
            cells[j]=stripMarkdown(parts[j].trim());
        }

        return cells;
    }

    private String stripMarkdown(String text){
        if(text==null)return "";

        return text
                .replaceAll("\\*\\*(.+?)\\*\\*","$1")
                .replaceAll("\\*(.+?)\\*","$1")
                .replaceAll("`(.+?)`","$1")
                .replaceAll("__(.+?)__","$1")
                .trim();
    }

    private void drawRenderedPage(PDPageContentStream cs,PDDocument doc,
                                  List<LineToken> tokens)throws Exception{

        PDFont fontRegular=PDType1Font.HELVETICA;
        PDFont fontBold=PDType1Font.HELVETICA_BOLD;

        float y=PAGE_HEIGHT-MARGIN_TOP;

        List<int[]> tableRanges=detectTableRanges(tokens);

        int i=0;

        while(i<tokens.size()){
            LineToken t=tokens.get(i);

            int[] tableRange=findTableRange(tableRanges,i);

            if(tableRange!=null){
                y=drawTable(cs,doc,tokens,tableRange[0],tableRange[1],y,fontRegular,fontBold);
                i=tableRange[1];
                continue;
            }

            switch(t.type){

                case H1:{
                    y-=8f;
                    cs.setNonStrokingColor(0.12f,0.29f,0.56f);
                    cs.addRect(MARGIN_LEFT,y-2,CONTENT_WIDTH,2f);
                    cs.fill();
                    y-=4f;
                    cs.setNonStrokingColor(0.12f,0.29f,0.56f);

                    drawTextLine(cs,fontBold,FONT_SIZE_H1,MARGIN_LEFT,y,
                            safe(stripMarkdown(t.text)));

                    y-=FONT_SIZE_H1+8f;
                    break;
                }

                case H2:{
                    y-=6f;
                    cs.setNonStrokingColor(0.18f,0.38f,0.62f);

                    drawTextLine(cs,fontBold,FONT_SIZE_H2,MARGIN_LEFT,y,
                            safe(stripMarkdown(t.text)));

                    cs.setStrokingColor(0.18f,0.38f,0.62f);
                    cs.setLineWidth(0.8f);
                    cs.moveTo(MARGIN_LEFT,y-3f);
                    cs.lineTo(MARGIN_LEFT+CONTENT_WIDTH,y-3f);
                    cs.stroke();

                    y-=FONT_SIZE_H2+8f;
                    break;
                }

                case H3:{
                    y-=5f;
                    cs.setNonStrokingColor(0.22f,0.22f,0.22f);

                    drawTextLine(cs,fontBold,FONT_SIZE_H3,MARGIN_LEFT,y,
                            safe(stripMarkdown(t.text)));

                    y-=FONT_SIZE_H3+5f;
                    break;
                }

                case BOLD_LABEL:{
                    String raw=t.text;
                    int boldEnd=raw.indexOf(":**");

                    if(boldEnd>=0){
                        String key=raw.substring(raw.indexOf("**")+2,boldEnd);
                        String value=raw.substring(boldEnd+3).trim();

                        value=value.replaceAll("\\*\\*$","").trim();

                        float xPos=MARGIN_LEFT;

                        cs.setNonStrokingColor(0.1f,0.1f,0.1f);

                        drawTextLine(cs,fontBold,FONT_SIZE_NORMAL,xPos,y,safe(key+": "));

                        float keyWidth=fontBold.getStringWidth(safe(key+": "))/1000f*FONT_SIZE_NORMAL;

                        if(!value.isEmpty()){
                            cs.setNonStrokingColor(0.15f,0.15f,0.15f);

                            drawTextLine(cs,fontRegular,FONT_SIZE_NORMAL,xPos+keyWidth,y,safe(value));
                        }
                    }else{
                        cs.setNonStrokingColor(0.1f,0.1f,0.1f);

                        drawTextLine(cs,fontBold,FONT_SIZE_NORMAL,MARGIN_LEFT,y,
                                safe(stripMarkdown(raw)));
                    }

                    y-=LINE_HEIGHT_NORMAL;
                    break;
                }

                case NORMAL:{
                    cs.setNonStrokingColor(0.15f,0.15f,0.15f);

                    List<String> wrapped=wrapText(safe(t.text),fontRegular,FONT_SIZE_NORMAL,CONTENT_WIDTH);

                    for(String wl:wrapped){
                        drawTextLine(cs,fontRegular,FONT_SIZE_NORMAL,MARGIN_LEFT,y,wl);
                        y-=LINE_HEIGHT_NORMAL;
                    }

                    break;
                }

                case DIVIDER:{
                    y-=4f;
                    cs.setStrokingColor(0.7f,0.7f,0.7f);
                    cs.setLineWidth(0.5f);
                    cs.moveTo(MARGIN_LEFT,y);
                    cs.lineTo(MARGIN_LEFT+CONTENT_WIDTH,y);
                    cs.stroke();
                    y-=6f;
                    break;
                }

                case BLANK:{
                    y-=LINE_HEIGHT_NORMAL*0.5f;
                    break;
                }

                case TABLE_SEPARATOR:{
                    break;
                }

                default:
                    break;
            }

            i++;
        }
    }

    private List<int[]> detectTableRanges(List<LineToken> tokens){
        List<int[]> ranges=new ArrayList<>();
        int i=0;

        while(i<tokens.size()){
            TokenType t=tokens.get(i).type;

            if(t==TokenType.TABLE_HEADER||t==TokenType.TABLE_ROW||t==TokenType.TABLE_SEPARATOR){
                int start=i;

                while(i<tokens.size()){
                    TokenType tt=tokens.get(i).type;

                    if(tt!=TokenType.TABLE_HEADER&&tt!=TokenType.TABLE_ROW&&tt!=TokenType.TABLE_SEPARATOR)
                        break;

                    i++;
                }

                ranges.add(new int[]{start,i});
            }else{
                i++;
            }
        }

        return ranges;
    }

    private int[] findTableRange(List<int[]> ranges,int idx){
        for(int[] r:ranges){
            if(r[0]==idx)return r;
        }

        return null;
    }

    private float drawTable(PDPageContentStream cs,PDDocument doc,
                            List<LineToken> tokens,int start,int end,
                            float y,PDFont fontRegular,PDFont fontBold)throws Exception{

        List<LineToken> rows=new ArrayList<>();

        for(int i=start;i<end;i++){
            LineToken t=tokens.get(i);

            if(t.type!=TokenType.TABLE_SEPARATOR)rows.add(t);
        }

        if(rows.isEmpty())return y;

        int colCount=rows.get(0).cells!=null?rows.get(0).cells.length:1;

        if(colCount==0)colCount=1;

        float[] colWidths=computeColumnWidths(rows,colCount,fontRegular,fontBold);

        float cellPadH=5f;
        float cellPadV=3f;
        float rowHeight=LINE_HEIGHT_TABLE+cellPadV*2;

        y-=6f;

        for(int ri=0;ri<rows.size();ri++){
            LineToken row=rows.get(ri);
            boolean isHeader=row.isTableHeader||(ri==0&&row.type==TokenType.TABLE_HEADER);

            String[] cells=row.cells!=null?row.cells:new String[]{row.text};

            if(isHeader){
                cs.setNonStrokingColor(0.18f,0.38f,0.62f);
                cs.addRect(MARGIN_LEFT,y-rowHeight,CONTENT_WIDTH,rowHeight);
                cs.fill();
            }else if(ri%2==0){
                cs.setNonStrokingColor(0.95f,0.96f,0.98f);
                cs.addRect(MARGIN_LEFT,y-rowHeight,CONTENT_WIDTH,rowHeight);
                cs.fill();
            }

            float xCursor=MARGIN_LEFT;

            for(int ci=0;ci<colCount;ci++){
                String cellText=ci<cells.length?safe(cells[ci]):"";
                PDFont cellFont=isHeader?fontBold:fontRegular;
                float fs=FONT_SIZE_TABLE;

                if(isHeader){
                    cs.setNonStrokingColor(1f,1f,1f);
                }else{
                    cs.setNonStrokingColor(0.1f,0.1f,0.1f);
                }

                float maxCellWidth=colWidths[ci]-cellPadH*2;

                cellText=truncateToWidth(cellText,cellFont,fs,maxCellWidth);

                drawTextLine(cs,cellFont,fs,xCursor+cellPadH,y-rowHeight+cellPadV+2f,cellText);

                xCursor+=colWidths[ci];
            }

            cs.setStrokingColor(0.65f,0.73f,0.82f);
            cs.setLineWidth(0.5f);
            cs.addRect(MARGIN_LEFT,y-rowHeight,CONTENT_WIDTH,rowHeight);
            cs.stroke();

            float xDiv=MARGIN_LEFT;

            for(int ci=0;ci<colCount-1;ci++){
                xDiv+=colWidths[ci];
                cs.moveTo(xDiv,y);
                cs.lineTo(xDiv,y-rowHeight);
                cs.stroke();
            }

            y-=rowHeight;
        }

        y-=6f;
        return y;
    }

    private float[] computeColumnWidths(List<LineToken> rows,int colCount,
                                        PDFont fontRegular,PDFont fontBold)throws Exception{

        float[] maxWidths=new float[colCount];
        float minColWidth=35f;

        for(LineToken row:rows){
            if(row.cells==null)continue;

            for(int ci=0;ci<colCount&&ci<row.cells.length;ci++){
                String cellText=safe(row.cells[ci]);
                PDFont font=row.isTableHeader?fontBold:fontRegular;
                float w=font.getStringWidth(cellText)/1000f*FONT_SIZE_TABLE+12f;

                if(w>maxWidths[ci])maxWidths[ci]=w;
            }
        }

        float totalDesired=0;

        for(float w:maxWidths)totalDesired+=Math.max(w,minColWidth);

        float[] colWidths=new float[colCount];

        if(totalDesired<=CONTENT_WIDTH){
            for(int ci=0;ci<colCount;ci++){
                colWidths[ci]=Math.max(maxWidths[ci],minColWidth);
            }

            float used=0;

            for(int ci=0;ci<colCount-1;ci++)used+=colWidths[ci];

            colWidths[colCount-1]=Math.max(CONTENT_WIDTH-used,minColWidth);
        }else{
            float scale=CONTENT_WIDTH/totalDesired;
            float used=0;

            for(int ci=0;ci<colCount-1;ci++){
                colWidths[ci]=Math.max(maxWidths[ci]*scale,minColWidth);
                used+=colWidths[ci];
            }

            colWidths[colCount-1]=Math.max(CONTENT_WIDTH-used,minColWidth);
        }

        return colWidths;
    }

    private void drawTextLine(PDPageContentStream cs,PDFont font,float fontSize,
                              float x,float y,String text)throws Exception{

        if(text==null||text.isEmpty())return;

        cs.beginText();
        cs.setFont(font,fontSize);
        cs.newLineAtOffset(x,y);
        cs.showText(text);
        cs.endText();
    }

    private List<String> wrapText(String text,PDFont font,float fontSize,float maxWidth)
            throws Exception{

        List<String> result=new ArrayList<>();

        if(text==null||text.isEmpty()){
            result.add("");
            return result;
        }

        String[] words=text.split(" ");
        StringBuilder current=new StringBuilder();

        for(String word:words){
            String test=current.length()==0?word:current+" "+word;
            float w=font.getStringWidth(test)/1000f*fontSize;

            if(w>maxWidth&&current.length()>0){
                result.add(current.toString());
                current=new StringBuilder(word);
            }else{
                current=new StringBuilder(test);
            }
        }

        if(current.length()>0)result.add(current.toString());

        return result;
    }

    private String truncateToWidth(String text,PDFont font,float fontSize,float maxWidth)
            throws Exception{

        if(text==null||text.isEmpty())return "";

        float w=font.getStringWidth(text)/1000f*fontSize;

        if(w<=maxWidth)return text;

        int len=text.length();

        while(len>0){
            String sub=text.substring(0,len)+(len<text.length()?"…":"");

            if(font.getStringWidth(sub)/1000f*fontSize<=maxWidth)return sub;

            len--;
        }

        return "";
    }

    private String safe(String text){
        if(text==null)return "";

        return text
                .replace('\u2019','\'')
                .replace('\u2018','\'')
                .replace('\u201C','"')
                .replace('\u201D','"')
                .replace('\u2013','-')
                .replace('\u2014','-')
                .replace('\u00A0',' ')
                .replaceAll("[^\\x20-\\x7E]"," ")
                .trim();
    }

    private List<String> splitIntoPages(String text,int linesPerPage){
        String[] lines=text.split("\n");
        List<String> pages=new ArrayList<>();
        StringBuilder page=new StringBuilder();
        int count=0;

        for(String line:lines){
            page.append(line).append("\n");
            count++;

            if(count>=linesPerPage){
                pages.add(page.toString());
                page=new StringBuilder();
                count=0;
            }
        }

        if(page.length()>0)pages.add(page.toString());
        if(pages.isEmpty())pages.add(text);

        return pages;
    }
}