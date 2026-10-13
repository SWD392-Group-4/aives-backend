package com.aives.integration.ai;

import java.util.List;
import java.util.Map;

/**
 * 1 lan goi LLM, yeu cau tra ve JSON dung theo schema.
 *
 * @param systemInstruction  vai tro va luat cho model
 * @param userPrompt         du lieu cua lan goi nay
 * @param responseSchema     schema JSON (kieu OpenAPI rut gon cua Gemini: type, properties, required, enum)
 * @param attachments        file gui kem cho model doc (vd file PDF), co the rong
 * @param longRunning        true = viec nang (cham diem, doc file) duoc cho lau hon; false = can tra loi nhanh (hoi xoay)
 * @param documentExtraction true = chi tach du lieu tu file giang vien gui len (khong can suy luan sau):
 *                           Gemini dung model rieng nhanh hon neu co cau hinh (GEMINI_IMPORT_MODEL)
 */
public record LlmRequest(String systemInstruction, String userPrompt, Map<String, Object> responseSchema,
                         List<Attachment> attachments, boolean longRunning, boolean documentExtraction) {

    /** File gui kem, vd mimeType = "application/pdf". */
    public record Attachment(String mimeType, byte[] data) {
    }

    public LlmRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static LlmRequest quick(String systemInstruction, String userPrompt, Map<String, Object> responseSchema) {
        return new LlmRequest(systemInstruction, userPrompt, responseSchema, List.of(), false, false);
    }

    public static LlmRequest longRunning(String systemInstruction, String userPrompt, Map<String, Object> responseSchema) {
        return new LlmRequest(systemInstruction, userPrompt, responseSchema, List.of(), true, false);
    }

    public LlmRequest withAttachment(String mimeType, byte[] data) {
        return new LlmRequest(systemInstruction, userPrompt, responseSchema,
                List.of(new Attachment(mimeType, data)), longRunning, documentExtraction);
    }

    /** Danh dau la viec doc / tach du lieu tu file (nhap cau hoi, nhap rubric). */
    public LlmRequest forDocumentExtraction() {
        return new LlmRequest(systemInstruction, userPrompt, responseSchema, attachments, longRunning, true);
    }
}
