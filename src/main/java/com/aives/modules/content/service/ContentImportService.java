package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.ContentImportResponse;

/**
 * Doc file (pdf / docx / txt) giang vien gui len bang AI. Rubric va cau hoi gui RIENG, giong 2 trang tren web:
 * trang Rubric gui file rubric, trang Ngan hang cau hoi gui file cau hoi.
 */
public interface ContentImportService {

    /**
     * File cau hoi + dap an -> 1 bai hoc, 1 chu de va cac cau hoi NHAP.
     *
     * @param rubricId rubric gan cho ca bo cau hoi (phai la rubric cua nguoi gui); null = chua gan, chon khi duyet
     */
    ContentImportResponse importQuestions(UserPrincipal actor, String fileName, byte[] fileBytes, String rubricId);

    /** File tieu chi cham diem -> 1 hoac nhieu rubric. */
    ContentImportResponse importRubrics(UserPrincipal actor, String fileName, byte[] fileBytes);
}
