package com.apps.mindlog.after.repository;
import com.apps.mindlog.after.entity.AfterLog;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface AfterLogRepository extends JpaRepository<AfterLog,Long>{
    Optional<AfterLog> findByBeforeLogId(Long beforeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AfterLog a where a.id=:id")
    Optional<AfterLog> lockById(Long id);
}
