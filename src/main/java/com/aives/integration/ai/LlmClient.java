package com.aives.integration.ai;

import java.util.Map;

/**
 * Cong goi mo hinh ngon ngu (LLM). Nghiep vu chi phu thuoc interface nay,
 * doi nha cung cap chi can them 1 class implement.
 */
public interface LlmClient {

    /** false = chua cau hinh API key, moi lan goi se nem LlmException. */
    boolean isEnabled();

    /**
     * Goi model va yeu cau tra ve JSON dung theo schema.
     *
     * @return chuoi JSON model tra ve
     * @throws LlmException khi khong goi duoc hoac model khong tra noi dung
     */
    String generateJson(LlmRequest request);

    /** Goi nhanh, khong kem file (dung cho quyet dinh hoi xoay trong luc thi). */
    default String generateJson(String systemInstruction, String userPrompt, Map<String, Object> responseSchema) {
        return generateJson(LlmRequest.quick(systemInstruction, userPrompt, responseSchema));
    }
}
