package com.aives.modules.exam.dto;

import java.util.List;
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
public class LiveExamMonitorResponse {

    private String examId;
    private String examTitle;
    private int totalStudentsJoined;
    private int totalCompleted;
    private int totalInProgress;
    private List<LiveExamineeResponse> examinees;
}
