package com.apps.mindlog.before.repository;

import com.apps.mindlog.before.entity.BeforeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface BeforeLogRepository extends JpaRepository<BeforeLog, Long> {
    Optional<BeforeLog> findByIdAndUser_Id(Long id, Long userId);

    @Query(value = """
            select b from BeforeLog b join fetch b.situationType
            where b.user.id = :userId
              and (:hasAfter is null or (:hasAfter = false and not exists (
                  select a.id from AfterLog a where a.beforeLogId = b.id and a.recordStatus = com.apps.mindlog.after.entity.RecordStatus.FINALIZED
              )) or (:hasAfter = true and exists (
                  select a.id from AfterLog a where a.beforeLogId = b.id and a.recordStatus = com.apps.mindlog.after.entity.RecordStatus.FINALIZED
              )))
            """,
            countQuery = """
            select count(b) from BeforeLog b
            where b.user.id = :userId
              and (:hasAfter is null or (:hasAfter = false and not exists (
                  select a.id from AfterLog a where a.beforeLogId = b.id and a.recordStatus = com.apps.mindlog.after.entity.RecordStatus.FINALIZED
              )) or (:hasAfter = true and exists (
                  select a.id from AfterLog a where a.beforeLogId = b.id and a.recordStatus = com.apps.mindlog.after.entity.RecordStatus.FINALIZED
              )))
            """)
    Page<BeforeLog> findList(@Param("userId") long userId, @Param("hasAfter") Boolean hasAfter, Pageable pageable);

}
