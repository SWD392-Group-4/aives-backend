package com.aives.integration.ai;

/** Loi khi goi LLM (mang, het quota, sai key, model khong tra noi dung...). */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
