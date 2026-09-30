package com.apps.mindlog.reference.repository;

import com.apps.mindlog.reference.entity.BodySymptom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodySymptomRepository extends JpaRepository<BodySymptom, Long> {
    List<BodySymptom> findAllByOrderByDisplayOrderAscIdAsc();
}
