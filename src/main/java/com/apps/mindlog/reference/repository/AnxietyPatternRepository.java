package com.apps.mindlog.reference.repository;

import com.apps.mindlog.reference.entity.AnxietyPattern;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnxietyPatternRepository extends JpaRepository<AnxietyPattern, Long> {
    List<AnxietyPattern> findAllByOrderByDisplayOrderAscIdAsc();
}
