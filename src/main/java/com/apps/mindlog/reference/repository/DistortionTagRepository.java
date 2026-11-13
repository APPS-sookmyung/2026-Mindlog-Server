package com.apps.mindlog.reference.repository;

import com.apps.mindlog.reference.entity.DistortionTag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistortionTagRepository extends JpaRepository<DistortionTag, Long> {
    List<DistortionTag> findAllByOrderByDisplayOrderAscIdAsc();
}
