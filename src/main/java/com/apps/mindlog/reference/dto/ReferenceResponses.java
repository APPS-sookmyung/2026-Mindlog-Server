package com.apps.mindlog.reference.dto;

import java.util.List;

public final class ReferenceResponses {
    private ReferenceResponses() { }
    public record Catalog<T>(List<T> content) {
        public Catalog { content = List.copyOf(content); }
    }
    public record Choice(Long id, String name) { }
    public record DescribedChoice(Long id, String name, String description) { }
    public record Emotion(Long id, String code, String name) { }
    public record Guide(Long id, String name, String illustrationKey, String definition,
            List<String> exampleThoughts, List<String> selfQuestions, List<String> reframes, boolean active) { }
    public record Solution(Long id, String category, String title, String description, int displayOrder) { }
}
