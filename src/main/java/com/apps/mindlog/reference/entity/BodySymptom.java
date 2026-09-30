package com.apps.mindlog.reference.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "body_symptoms")
public class BodySymptom extends ReferenceEntity {
    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;
    @Column(name = "symptom_type", nullable = false, columnDefinition = "text")
    private String symptomType;
    @Column(name = "onboarding_selectable", nullable = false)
    private boolean onboardingSelectable;
    @Column(name = "after_selectable", nullable = false)
    private boolean afterSelectable;

    protected BodySymptom() { }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSymptomType() { return symptomType; }
    public boolean isOnboardingSelectable() { return onboardingSelectable; }
    public boolean isAfterSelectable() { return afterSelectable; }
}
