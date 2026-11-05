package com.aives.modules.vivaroom.dto;

import com.aives.modules.vivaroom.enums.InterviewStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trang thai phong van tra cho sinh vien sau moi thao tac (bat dau, tra loi, bo qua).
 * Khi status = QUESTION thi cac truong ve cau hoi moi co gia tri.
 * Khong chua dap an, tu khoa hay ly do quyet dinh cua AI.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterviewStateResponse {

    private InterviewStatus status;

    // false = backend chua co ANTHROPIC_API_KEY nen khong co cau hoi xoay
    private boolean aiEnabled;

    // true = lan quyet dinh gan nhat AI bi loi (het quota, mat mang...) nen he thong tu chuyen cau tiep theo
    private boolean aiFallback;

    // false = backend chua co GOOGLE_SPEECH_CREDENTIALS: FE tu nhan dang giong noi / tu doc cau hoi (hoac go chu)
    private boolean sttEnabled;

    private boolean ttsEnabled;

    private Integer totalQuestions;

    /* ----- Chi co khi status = QUESTION ----- */

    // Id luot hoi hien tai, FE gui lai khi tra loi / bo qua
    private String exchangeId;

    // MAIN = cau hoi goc, FOLLOW_UP = cau hoi xoay
    private String type;

    private String questionText;

    // Hinh ve cua cau hoi goc (data:image/png;base64,...), null = cau hoi khong co hinh
    private String questionImage;

    // Cau hoi goc thu may (bat dau tu 1)
    private Integer questionNo;

    // Cau hoi xoay thu may cua cau goc nay (0 = dang o cau goc)
    private Integer followUpNo;

    private Integer maxFollowUps;

    private String bloomLevel;

    private String rubricName;

    private Integer prepareSeconds;

    private Integer answerSeconds;
}
