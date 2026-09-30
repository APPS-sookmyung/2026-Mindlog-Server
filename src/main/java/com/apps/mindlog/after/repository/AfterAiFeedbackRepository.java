package com.apps.mindlog.after.repository;
import com.apps.mindlog.after.entity.AfterAiFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AfterAiFeedbackRepository extends JpaRepository<AfterAiFeedback,Long>{
    Optional<AfterAiFeedback> findByAfterLogId(Long afterId);
}
