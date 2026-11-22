package com.apps.mindlog.user.entity;

import com.apps.mindlog.reference.entity.EmotionCharacter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Minimal users mapping used by domain relations until the user feature is implemented. */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "representative_character_id", nullable = false)
    private EmotionCharacter representativeCharacter;

    protected User() { }
    public Long getId() { return id; }
}
