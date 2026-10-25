package com.aives.modules.content.dto;

import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.enums.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionFilter {

    private QuestionStatus status;
    private QuestionDifficulty difficulty;
}