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
import jakarta.persistence.Transient;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "email", nullable = false)
    private String email;

    @jakarta.persistence.Column(name = "password_hash")
    private String passwordHash;

    @jakarta.persistence.Column(name = "nickname", nullable = false)
    private String nickname;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "representative_character_id", nullable = false)
    private EmotionCharacter representativeCharacter;

    @jakarta.persistence.Column(name = "current_emotion_code")
    private String currentEmotionCode;

    @jakarta.persistence.Column(name = "baseline_discomfort_score")
    private Integer baselineDiscomfortScore;

    @jakarta.persistence.Column(name = "gender")
    private String gender;

    @jakarta.persistence.Column(name = "birth_year")
    private Integer birthYear;

    @jakarta.persistence.Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @jakarta.persistence.Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @jakarta.persistence.Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Column(name = "account_status", nullable = false)
    private String accountStatus;

    @Transient
    private String loginProvider;

    protected User() { }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public EmotionCharacter getRepresentativeCharacter() { return representativeCharacter; }
    public String getCurrentEmotionCode() { return currentEmotionCode; }
    public Integer getBaselineDiscomfortScore() { return baselineDiscomfortScore; }
    public String getGender() { return gender; }
    public Integer getBirthYear() { return birthYear; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getAccountStatus() { return accountStatus; }
    public String getLoginProvider() { return loginProvider; }
    public void setLoginProvider(String loginProvider) { this.loginProvider = loginProvider; }
    public void touch(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void updateProfile(String nickname, EmotionCharacter representativeCharacter) {
        this.nickname = nickname;
        this.representativeCharacter = representativeCharacter;
    }
    public void completeOnboarding(String emotionCode, int discomfortScore, Instant completedAt) {
        this.currentEmotionCode = emotionCode;
        this.baselineDiscomfortScore = discomfortScore;
        this.onboardingCompleted = true;
        this.onboardingCompletedAt = completedAt;
        this.accountStatus = "active";
        this.updatedAt = completedAt;
    }
}
