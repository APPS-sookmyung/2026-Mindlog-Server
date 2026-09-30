package com.apps.mindlog.after.repository;
import com.apps.mindlog.after.entity.AiInsight;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiInsightRepository extends JpaRepository<AiInsight,Long>{
    List<AiInsight> findByAfterLogIdOrderByDisplayOrderAscIdAsc(Long afterId);
    void deleteByAfterLogId(Long afterId);
}

