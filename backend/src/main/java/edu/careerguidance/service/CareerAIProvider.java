package edu.careerguidance.service;

import java.util.Optional;

/** Provider boundary for career guidance text generation. Implementations keep secrets on the server. */
public interface CareerAIProvider {
    Optional<String> complete(String context, String prompt);
    boolean configured();
    String providerName();
}
