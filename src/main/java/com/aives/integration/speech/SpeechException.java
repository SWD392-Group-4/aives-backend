package com.aives.integration.speech;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;

/**
 * Loi khi goi Google STT / TTS. Ke thua BusinessException nen GlobalExceptionHandler
 * tu tra ve dung HTTP status + ma loi trong ErrorCode.
 */
public class SpeechException extends BusinessException {

    public SpeechException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SpeechException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message);
        initCause(cause);
    }
}
