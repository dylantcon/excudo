package com.excudo.core.llm;

/**
 * An LLM provider request failed at the transport or API level (bad key,
 * HTTP error, timeout). Distinct from a normal model reply so callers can
 * report it as an error instead of treating the error body as the model's
 * answer.
 */
public class LLMApiException extends RuntimeException {

    private final int statusCode;

    public LLMApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public LLMApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** HTTP status, or -1 for connection-level failures. */
    public int getStatusCode() {
        return statusCode;
    }
}
