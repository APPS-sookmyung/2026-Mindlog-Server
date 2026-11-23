package com.apps.mindlog.reference.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "distortion_tags")
public class DistortionTag extends ReferenceEntity {
    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;
    @Column(name = "definition", nullable = false, columnDefinition = "text")
    private String definition;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "example_thoughts", nullable = false, columnDefinition = "jsonb")
    private List<String> exampleThoughts;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "self_questions", nullable = false, columnDefinition = "jsonb")
    private List<String> selfQuestions;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reframes", nullable = false, columnDefinition = "jsonb")
    private List<String> reframes;
    @Column(name = "theme_color", nullable = true, columnDefinition = "text")
    private String themeColor;
    @Column(name = "illustration_key", nullable = true, columnDefinition = "text")
    private String illustrationKey;

    protected DistortionTag() { }

    public String getName() { return name; }
    public String getDefinition() { return definition; }
    public List<String> getExampleThoughts() { return List.copyOf(exampleThoughts); }
    public List<String> getSelfQuestions() { return List.copyOf(selfQuestions); }
    public List<String> getReframes() { return List.copyOf(reframes); }
    public String getThemeColor() { return themeColor; }
    public String getIllustrationKey() { return illustrationKey; }
}
