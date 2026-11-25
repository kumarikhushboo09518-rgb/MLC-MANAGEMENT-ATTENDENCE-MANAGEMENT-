package com.example.localization

enum class AppLanguage(val code: String, val nativeLabel: String, val subtitle: String) {
    ENGLISH("en", "English", "Continue in English"),
    HINDI("hi", "हिंदी (Hindi)", "हिंदी में जारी रखें");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}
