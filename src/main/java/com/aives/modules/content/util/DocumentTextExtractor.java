package com.aives.modules.content.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lay phan chu trong file Word .docx ma khong can thu vien ngoai:
 * .docx la 1 file zip, noi dung nam trong word/document.xml.
 */
public final class DocumentTextExtractor {

    private static final String DOCUMENT_ENTRY = "word/document.xml";
    // Chan file nen "bom": document.xml giai nen ra toi da bay nhieu byte
    private static final int MAX_DOCUMENT_XML_BYTES = 20 * 1024 * 1024;

    private DocumentTextExtractor() {
    }

    /** @throws IllegalArgumentException khi file khong phai .docx hop le */
    public static String extractDocxText(byte[] fileBytes) {
        byte[] xml = readDocumentXml(fileBytes);
        if (xml == null) {
            throw new IllegalArgumentException("Không đọc được file Word: thiếu nội dung tài liệu");
        }
        try {
            return xmlToText(xml);
        } catch (XMLStreamException ex) {
            throw new IllegalArgumentException("Không đọc được file Word: nội dung bị lỗi", ex);
        }
    }

    private static byte[] readDocumentXml(byte[] fileBytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!DOCUMENT_ENTRY.equals(entry.getName())) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    if (out.size() + read > MAX_DOCUMENT_XML_BYTES) {
                        throw new IllegalArgumentException("File Word quá lớn");
                    }
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
            return null;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không đọc được file Word", ex);
        }
    }

    /** Moi doan van (w:p) va moi dong bang (w:tr) thanh 1 dong; cac o trong 1 dong bang cach nhau bang " | ". */
    private static String xmlToText(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Khong doc DTD / thuc the ngoai (tranh XXE)
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");

        StringBuilder text = new StringBuilder();
        boolean insideText = false;
        int tableDepth = 0;
        try (InputStream in = new ByteArrayInputStream(xml)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "t" -> insideText = true;
                        case "tab" -> text.append('\t');
                        case "br", "cr" -> text.append('\n');
                        case "tbl" -> tableDepth++;
                        default -> { }
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && insideText) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "t" -> insideText = false;
                        // Doan van nam trong o bang thi chi cach nhau bang dau cach, het dong bang moi xuong dong
                        case "p" -> text.append(tableDepth > 0 ? ' ' : '\n');
                        case "tc" -> text.append(" | ");
                        case "tr" -> text.append('\n');
                        case "tbl" -> tableDepth = Math.max(0, tableDepth - 1);
                        default -> { }
                    }
                }
            }
            reader.close();
        } catch (IOException ex) {
            throw new XMLStreamException(ex);
        }
        return text.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
