package com.aives.modules.grading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 1 don phuc khao diem cua 1 cau hoi. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppealResponse {
    private String id;
    private String questionGradeId;
    private String questionText;
    private String attemptId;
    private String examId;
    private String examTitle;
    private String studentId;
    private String studentName;
    private String reason;
    // PENDING / ACCEPTED / REJECTED
    private String status;
    // Diem chot cua cau hoi luc gui don va sau khi giang vien xu ly
    private BigDecimal scoreBefore;
    private BigDecimal scoreAfter;
    // Thang diem cua rubric (thuong la 10)
    private BigDecimal maxScore;
    // Phan hoi cua giang vien cho sinh vien
    private String response;
    private String resolvedBy;
    private String resolvedByName;
    private Instant createdAt;
    private Instant resolvedAt;
}
