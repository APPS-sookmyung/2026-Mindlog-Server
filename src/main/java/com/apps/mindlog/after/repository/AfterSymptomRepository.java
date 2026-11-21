package com.apps.mindlog.after.repository;
import com.apps.mindlog.after.entity.AfterSymptom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AfterSymptomRepository extends JpaRepository<AfterSymptom,Long>{
    List<AfterSymptom> findByAfterLogIdOrderByBodySymptomIdAsc(Long afterId);
    void deleteByAfterLogId(Long afterId);
}

