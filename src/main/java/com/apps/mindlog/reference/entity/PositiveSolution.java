package com.apps.mindlog.reference.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "positive_solutions")
public class PositiveSolution extends ReferenceEntity {
    @Column(name = "situation_type_id", nullable = true)
    private Long situationTypeId;
    @Column(name = "category_code", nullable = false, columnDefinition = "text")
    private String categoryCode;
    @Column(name = "category_name", nullable = false, columnDefinition = "text")
    private String categoryName;
    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;
    @Column(name = "solution_content", nullable = false, columnDefinition = "text")
    private String solutionContent;

    protected PositiveSolution() { }

    public Long getSituationTypeId() { return situationTypeId; }
    public String getCategoryCode() { return categoryCode; }
    public String getCategoryName() { return categoryName; }
    public String getTitle() { return title; }
    public String getSolutionContent() { return solutionContent; }
}
