package com.apps.mindlog.reference.entity;

import com.apps.mindlog.global.common.BaseTimeEntity;
import jakarta.persistence.*;

@MappedSuperclass
public abstract class ReferenceEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private int displayOrder;
    @Column(nullable = false)
    private boolean active;

    public Long getId() { return id; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
