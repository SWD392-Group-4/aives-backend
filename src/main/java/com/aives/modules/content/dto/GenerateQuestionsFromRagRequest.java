package com.aives.modules.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class GenerateQuestionsFromRagRequest {

    @NotBlank(message = "Chủ đề không được để trống")
    private String topicId;

    @NotEmpty(message = "Cần chọn ít nhất 1 tài liệu để sinh câu hỏi")
    private List<String> materialIds;

    @Builder.Default
    private Integer count = 5;

    @Builder.Default
    private Integer rememberCount = 1;

    @Builder.Default
    private Integer understandCount = 2;

    @Builder.Default
    private Integer applyCount = 1;

    @Builder.Default
    private Integer analyzeCount = 1;

    private java.util.Map<String, Integer> bloomDistribution;

    /** Alias từ frontend: questionCount -> count */
    public void setQuestionCount(Integer questionCount) {
        if (questionCount != null && questionCount > 0) {
            this.count = questionCount;
        }
    }

    public Integer getQuestionCount() {
        return this.count;
    }

    /**
     * Tự động giải quyết số lượng câu hỏi của từng mức Bloom theo tỷ lệ cấu hình (BR-BANK-004)
     */
    public void resolveBloomCounts() {
        int total = (count != null && count > 0) ? count : 5;
        this.count = total;

        if (bloomDistribution != null && !bloomDistribution.isEmpty()) {
            int remPct = bloomDistribution.getOrDefault("REMEMBER", 0);
            int undPct = bloomDistribution.getOrDefault("UNDERSTAND", 0);
            int appPct = bloomDistribution.getOrDefault("APPLY", 0);
            int anaPct = bloomDistribution.getOrDefault("ANALYZE", 0);
            int totalPct = remPct + undPct + appPct + anaPct;

            if (totalPct > 0) {
                // Trường hợp đặc biệt: 100% ở 1 mức
                if (remPct == totalPct) {
                    this.rememberCount = total;
                    this.understandCount = 0;
                    this.applyCount = 0;
                    this.analyzeCount = 0;
                    return;
                }
                if (undPct == totalPct) {
                    this.rememberCount = 0;
                    this.understandCount = total;
                    this.applyCount = 0;
                    this.analyzeCount = 0;
                    return;
                }
                if (appPct == totalPct) {
                    this.rememberCount = 0;
                    this.understandCount = 0;
                    this.applyCount = total;
                    this.analyzeCount = 0;
                    return;
                }
                if (anaPct == totalPct) {
                    this.rememberCount = 0;
                    this.understandCount = 0;
                    this.applyCount = 0;
                    this.analyzeCount = total;
                    return;
                }

                // Phân bổ tỷ lệ thông thường
                this.rememberCount = (int) Math.round((double) total * remPct / totalPct);
                this.understandCount = (int) Math.round((double) total * undPct / totalPct);
                this.applyCount = (int) Math.round((double) total * appPct / totalPct);
                this.analyzeCount = total - this.rememberCount - this.understandCount - this.applyCount;

                if (this.analyzeCount < 0) {
                    this.analyzeCount = 0;
                }
            }
        }
    }
}

