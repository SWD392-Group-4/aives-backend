package com.aives.modules.grading.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.AppealResponse;
import java.math.BigDecimal;
import java.util.List;

/** Phuc khao diem: sinh vien gui don cho tung cau hoi sau khi ket qua duoc cong bo, giang vien tao phien xu ly. */
public interface AppealService {

    /** Sinh vien gui don phuc khao cho 1 cau hoi cua luot thi da cong bo. Moi cau chi co 1 don dang cho xu ly. */
    AppealResponse submit(String studentId, String questionGradeId, String reason);

    List<AppealResponse> listMine(String studentId);

    /** Don phuc khao cua cac phien thi do giang vien tao (admin: moi phien). */
    List<AppealResponse> listForReview(UserPrincipal actor);

    /**
     * Giang vien xu ly don. ACCEPTED: diem chot cua cau hoi doi thanh scoreAfter va tong diem luot thi duoc tinh lai.
     * REJECTED: diem giu nguyen.
     */
    AppealResponse resolve(UserPrincipal actor, String appealId, String status, BigDecimal scoreAfter, String response);
}
