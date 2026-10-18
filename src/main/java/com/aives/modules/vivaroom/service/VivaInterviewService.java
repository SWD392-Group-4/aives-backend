package com.aives.modules.vivaroom.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.vivaroom.dto.InterviewExchangeResponse;
import com.aives.modules.vivaroom.dto.InterviewStateResponse;
import java.util.List;

/** Luong phong van cua 1 luot thi: phat cau hoi, nhan cau tra loi, hoi xoay, chuyen cau, ket thuc. */
public interface VivaInterviewService {

    /** Bat dau phong van, hoac lay lai cau hoi dang do neu sinh vien tai lai trang. */
    InterviewStateResponse start(String attemptId, String studentId);

    /** Nop cau tra loi cho cau hoi hien tai. AI quyet dinh hoi xoay hay chuyen cau tiep theo. */
    InterviewStateResponse answer(String attemptId, String studentId, String exchangeId, String transcript);

    /** Bo qua cau hoi hien tai (ca cac cau hoi xoay cua no), chuyen sang cau hoi goc tiep theo. */
    InterviewStateResponse skip(String attemptId, String studentId, String exchangeId);

    /**
     * Noi dung cau hoi DANG CHO tra loi (dung exchangeId FE gui len) cua luot thi dang lam.
     * Dung de doc cau hoi thanh giong noi (TTS) va kiem tra truoc khi chuyen audio cau tra loi thanh chu (STT).
     */
    String currentQuestionText(String attemptId, String studentId, String exchangeId);

    /** Toan bo luot hoi - dap cua 1 luot thi, cho nguoi tao phien hoac admin xem lai. */
    List<InterviewExchangeResponse> listExchanges(String attemptId, UserPrincipal actor);
}
