package com.apps.mindlog.ai.common;

/** Safe, deliberately omits input, provider response and parser exception cause. */
public class InvalidLlmOutputException extends IllegalArgumentException {
    public InvalidLlmOutputException(){super("Invalid structured AI output");}
}
