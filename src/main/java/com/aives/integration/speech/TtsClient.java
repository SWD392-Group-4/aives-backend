package com.aives.integration.speech;

/** Text-to-Speech: doc cau hoi (do Gemini sinh ra) thanh giong noi. */
public interface TtsClient {

    /** true = da cau hinh Google TTS that; false = dang dung ban gia. */
    boolean isEnabled();

    /** @return audio MP3 */
    byte[] synthesize(String text);

    /** Audio MP3 dang base64 de gui trong JSON. FE phat bang: new Audio("data:audio/mpeg;base64," + audioBase64) */
    default String synthesizeToBase64(String text) {
        return java.util.Base64.getEncoder().encodeToString(synthesize(text));
    }
}
