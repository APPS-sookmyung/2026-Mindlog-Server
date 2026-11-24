package com.apps.mindlog.before.repository;

import com.apps.mindlog.before.entity.BeforeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BeforeLogRepository extends JpaRepository<BeforeLog, Long> {
    Optional<BeforeLog> findByIdAndUser_Id(Long id, Long userId);
}
