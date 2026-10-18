package com.aives.integration.speech;

/** Speech-to-Text: chuyen audio cau tra loi cua sinh vien thanh van ban. */
public interface SttClient {

    /** true = da cau hinh Google STT that; false = dang dung ban gia. */
    boolean isEnabled();

    /**
     * @param audioBytes audio webm/opus ghi tu trinh duyet (toi da ~60 giay)
     * @return van ban nhan dang duoc (co the rong neu khong nghe thay gi)
     */
    String transcribe(byte[] audioBytes);
}
