package com.apps.mindlog.reference.repository;

import com.apps.mindlog.reference.entity.PositiveSolution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositiveSolutionRepository extends JpaRepository<PositiveSolution, Long> {
    List<PositiveSolution> findAllByOrderByDisplayOrderAscIdAsc();
}
