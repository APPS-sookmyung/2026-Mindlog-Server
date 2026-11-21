package com.apps.mindlog.ai.job;

import java.util.Locale;

public enum JobKind {
    BEFORE_REBUTTAL, AFTER_ANALYSIS, DIARY_DRAFT, RECORD_INSIGHT;
    public String code() { return name().toLowerCase(Locale.ROOT); }
    public boolean temporary() { return this == BEFORE_REBUTTAL || this == DIARY_DRAFT; }
    public static JobKind fromCode(String code) { return valueOf(code.toUpperCase(Locale.ROOT)); }
}
