package com.apps.mindlog.reference.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "emotion_characters")
public class EmotionCharacter extends ReferenceEntity {
    @Column(name = "code", nullable = false, columnDefinition = "text")
    private String code;
    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    protected EmotionCharacter() { }

    public String getCode() { return code; }
    public String getName() { return name; }
}
