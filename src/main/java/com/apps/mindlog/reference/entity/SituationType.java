package com.apps.mindlog.reference.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "situation_types")
public class SituationType extends ReferenceEntity {
    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;
    @Column(name = "onboarding_selectable", nullable = false)
    private boolean onboardingSelectable;
    @Column(name = "before_selectable", nullable = false)
    private boolean beforeSelectable;

    protected SituationType() { }

    public String getName() { return name; }
    public boolean isOnboardingSelectable() { return onboardingSelectable; }
    public boolean isBeforeSelectable() { return beforeSelectable; }
}
