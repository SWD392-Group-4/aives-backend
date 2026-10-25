package com.aives.modules.content.enums;

public enum QuestionDifficulty {
    EASY,
    MEDIUM,
    HARD;

    public static QuestionDifficulty fromString(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return QuestionDifficulty.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
