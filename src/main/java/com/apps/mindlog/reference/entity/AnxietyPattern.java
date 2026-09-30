package com.apps.mindlog.reference.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "anxiety_patterns")
public class AnxietyPattern extends ReferenceEntity {
    @Column(name = "code", nullable = false, columnDefinition = "text")
    private String code;
    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    protected AnxietyPattern() { }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
