package com.apps.mindlog.reference.repository;

import com.apps.mindlog.reference.entity.EmotionCharacter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmotionCharacterRepository extends JpaRepository<EmotionCharacter, Long> {
    List<EmotionCharacter> findAllByOrderByDisplayOrderAscIdAsc();
}
