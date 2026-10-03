package com.aives.modules.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ket qua cua API xoa phien: DELETED (xoa han) hoac CANCELLED (da co sinh vien vao nen chi huy).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeleteExamSessionResponse {

    public static final String DELETED = "DELETED";
    public static final String CANCELLED = "CANCELLED";

    private String action;
}
