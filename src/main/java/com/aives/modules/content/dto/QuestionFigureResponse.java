package com.aives.modules.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hinh ve cua cau hoi, tra dang data URL (data:image/png;base64,...) de FE gan thang vao the img. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionFigureResponse {
    private String questionId;
    private String dataUrl;
    private Integer width;
    private Integer height;
}
