package com.apps.mindlog.after.repository;
import com.apps.mindlog.after.entity.AfterDistortion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AfterDistortionRepository extends JpaRepository<AfterDistortion,Long>{
    List<AfterDistortion> findByAfterLogIdOrderByIdAsc(Long afterId);
    void deleteByAfterLogId(Long afterId);
}

