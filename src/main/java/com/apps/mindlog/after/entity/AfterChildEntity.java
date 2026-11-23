package com.apps.mindlog.after.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass @EntityListeners(AuditingEntityListener.class)
public abstract class AfterChildEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long afterLogId;
    @CreatedDate @Column(nullable=false,updatable=false) private Instant createdAt;
    protected AfterChildEntity(){}
    protected AfterChildEntity(long afterId){if(afterId<=0)throw new IllegalArgumentException("After ID required");afterLogId=afterId;}
    public Long getId(){return id;}public Long getAfterLogId(){return afterLogId;}public Instant getCreatedAt(){return createdAt;}
}
