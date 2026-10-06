package com.aives.integration.ai;

/**
 * Dung khi CHUA co GEMINI_API_KEY: khong goi ra ngoai, moi lan goi deu bao loi.
 * Nghiep vu phong van bat loi nay va tu chuyen sang cau hoi tiep theo, nen bai thi van chay duoc.
 */
public class StubLlmClient implements LlmClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String generateJson(LlmRequest request) {
        throw new LlmException("Chưa cấu hình GEMINI_API_KEY nên không gọi được AI");
    }
}
