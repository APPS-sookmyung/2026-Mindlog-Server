package com.apps.mindlog.after.repository;
import com.apps.mindlog.after.entity.AfterAnalysisCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AfterAnalysisCardRepository extends JpaRepository<AfterAnalysisCard,Long>{
    List<AfterAnalysisCard> findByAfterLogIdOrderByDisplayOrderAscIdAsc(Long afterId);
    void deleteByAfterLogId(Long afterId);
}

