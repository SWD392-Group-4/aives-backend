package com.aives.modules.exam.dto;

import com.aives.modules.exam.enums.ExamSessionStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chi tra cho nguoi tao phien va ADMIN vi co chua passcode.
 * KHONG dung DTO nay cho API cua sinh vien.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSessionResponse {

    // Id phien thi, dong thoi la ma vao thi (AIVES_EXAM_yyyy_xxxxxx)
    private String id;
    private String passcode;
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private int durationMinutes;
    // So luot thi toi da cua moi sinh vien
    private int maxAttempts;
    private ExamSessionStatus status;

    // Nguoi tao phien
    private String ownerId;
    private String ownerName;

    // Tong so luot thi da tao trong phien (1 sinh vien thi 2 lan tinh la 2)
    private long attemptCount;

    // true: phien da mo, chi con sua duoc ten, mo ta va keo dai gio dong
    private boolean scheduleLocked;

    private LocalDateTime createdAt;
}
