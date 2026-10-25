package com.aives.modules.content.enums;

public enum QuestionStatus {
    DRAFT,
    APPROVED,
    REJECTED,
    ARCHIVED;

    public static QuestionStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return QuestionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DRAFT;
        }
    }
}
