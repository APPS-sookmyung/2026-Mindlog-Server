package com.apps.mindlog.before.entity;

import com.apps.mindlog.global.common.BaseTimeEntity;
import com.apps.mindlog.global.validation.VisibleLength;
import com.apps.mindlog.reference.entity.SituationType;
import com.apps.mindlog.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Entity
@Table(name = "before_logs")
public class BeforeLog extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "situation_type_id", nullable = false, updatable = false)
    private SituationType situationType;

    @Size(max = 300) @Column(name = "title", columnDefinition = "text")
    private String title;

    @Size(max = 1000) @Column(name = "description", columnDefinition = "text")
    private String description;

    @NotNull @Column(name = "scheduled_at", nullable = false)
    private LocalDate scheduledAt;

    @NotNull @Min(0) @Max(100)
    @Column(name = "expected_score", nullable = false)
    private Integer expectedScore;

    @Column(name = "expected_score_comment", columnDefinition = "text")
    private String expectedScoreComment;

    @NotNull @VisibleLength(min = 1, max = 300)
    @Column(name = "worst_scenario", nullable = false, columnDefinition = "text")
    private String worstScenario;

    protected BeforeLog() { }

    public static BeforeLog create(User user, SituationType situationType, LocalDate scheduledAt,
            int expectedScore, String worstScenario, String title, String description) {
        var log = new BeforeLog();
        log.user = user;
        log.situationType = situationType;
        log.scheduledAt = scheduledAt;
        log.expectedScore = expectedScore;
        log.worstScenario = worstScenario;
        log.title = title == null ? situationType.getName() : title.strip();
        log.description = description == null ? null : description.strip();
        return log;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public SituationType getSituationType() { return situationType; }
    public LocalDate getScheduledAt() { return scheduledAt; }
    public Integer getExpectedScore() { return expectedScore; }
    public String getWorstScenario() { return worstScenario; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
}
