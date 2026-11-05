package com.aives.modules.content.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Doc chu tu file tai lieu de dua vao prompt sinh cau hoi (khong can Vertex AI RAG).
 * Ho tro: .pdf (PDFBox), .pptx (doc XML trong file zip), .docx (DocumentTextExtractor).
 * File .ppt (dinh dang nhi phan cu) khong ho tro.
 */
public final class MaterialTextExtractor {

    private static final Pattern PPTX_SLIDE = Pattern.compile("^ppt/slides/slide(\\d+)\\.xml$");
    private static final Pattern PPTX_TEXT = Pattern.compile("<a:t>([^<]*)</a:t>");
    private static final Pattern PPTX_PARAGRAPH_END = Pattern.compile("</a:p>");

    private MaterialTextExtractor() {
    }

    public static boolean isSupported(String ext) {
        return "pdf".equals(ext) || "pptx".equals(ext) || "docx".equals(ext);
    }

    /** Tra ve chu da chuan hoa khoang trang; chuoi rong neu file khong co chu (vd PDF scan). */
    public static String extract(byte[] bytes, String ext) throws IOException {
        String raw = switch (ext) {
            case "pdf" -> extractPdf(bytes);
            case "pptx" -> extractPptx(bytes);
            case "docx" -> DocumentTextExtractor.extractDocxText(bytes);
            default -> throw new IOException("Không hỗ trợ đọc nội dung file ." + ext);
        };
        return normalize(raw);
    }

    private static String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static String extractPptx(byte[] bytes) throws IOException {
        // Giu dung thu tu slide 1, 2, 3...
        Map<Integer, String> slides = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Matcher m = PPTX_SLIDE.matcher(entry.getName());
                if (m.matches()) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    xml = PPTX_PARAGRAPH_END.matcher(xml).replaceAll("<a:t>\n</a:t>");
                    slides.put(Integer.parseInt(m.group(1)), collect(xml, PPTX_TEXT));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        slides.forEach((no, text) -> sb.append("--- Slide ").append(no).append(" ---\n").append(text).append('\n'));
        return sb.toString();
    }

    private static String collect(String xml, Pattern textTag) {
        StringBuilder sb = new StringBuilder();
        Matcher m = textTag.matcher(xml);
        while (m.find()) {
            sb.append(unescapeXml(m.group(1)));
        }
        return sb.toString();
    }

    private static String unescapeXml(String s) {
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", "")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
