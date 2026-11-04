package com.aives.integration.speech;

import java.util.List;

/** Speech-to-Text: chuyen audio cau tra loi cua sinh vien thanh van ban. */
public interface SttClient {

    /** true = da cau hinh Google STT that; false = dang dung ban gia. */
    boolean isEnabled();

    /**
     * @param audioBytes audio webm/opus ghi tu trinh duyet (toi da ~60 giay)
     * @return van ban nhan dang duoc (co the rong neu khong nghe thay gi)
     */
    String transcribe(byte[] audioBytes);

    /**
     * @param audioBytes audio webm/opus ghi tu trinh duyet (toi da ~60 giay)
     * @param phraseHints danh sach tu khoa goi y theo boi canh mon hoc / cau hoi (BR-VIVA-004)
     * @return van ban nhan dang duoc
     */
    default String transcribe(byte[] audioBytes, List<String> phraseHints) {
        return transcribe(audioBytes);
    }
}
