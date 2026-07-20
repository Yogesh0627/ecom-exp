package com.ecoexpress.ai.domain;

/** Outcome of an AI call (ai_log_status_chk in V7). */
public enum AiRequestStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    RATE_LIMITED
}
