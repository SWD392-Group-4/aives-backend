package com.aives.integration.speech;

import com.aives.common.exception.ErrorCode;

/** Dung khi CHUA cau hinh GOOGLE_SPEECH_CREDENTIALS: khong goi ra ngoai, moi lan goi deu bao loi. */
public class StubSttClient implements SttClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String transcribe(byte[] audioBytes) {
        throw new SpeechException(ErrorCode.SPEECH_UNAVAILABLE, ErrorCode.SPEECH_UNAVAILABLE.getMessage());
    }
}
