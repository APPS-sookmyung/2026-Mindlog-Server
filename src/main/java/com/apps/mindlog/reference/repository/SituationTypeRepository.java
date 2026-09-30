package com.apps.mindlog.reference.repository;

import com.apps.mindlog.reference.entity.SituationType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SituationTypeRepository extends JpaRepository<SituationType, Long> {
    List<SituationType> findAllByOrderByDisplayOrderAscIdAsc();
}
