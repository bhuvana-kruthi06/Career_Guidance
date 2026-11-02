package edu.careerguidance.service;

public class MatcherUnavailableException extends RuntimeException {
    public MatcherUnavailableException(String message) { super(message); }
    public MatcherUnavailableException(String message, Throwable cause) { super(message, cause); }
}
