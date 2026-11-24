package com.apps.mindlog.user.repository;

import com.apps.mindlog.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByIdAndAccountStatusNot(Long id, String accountStatus);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id and u.accountStatus <> 'deleting'")
    Optional<User> findForOnboardingUpdate(@Param("id") long id);

    @EntityGraph(attributePaths = "representativeCharacter")
    @Query("select u from User u where u.id=:id and u.accountStatus <> 'deleting'")
    Optional<User> findProfileById(@Param("id") long id);

    @Query(value = "select exists(select 1 from social_accounts where user_id=:id and provider='google')", nativeQuery = true)
    boolean existsGoogleAccount(@Param("id") long id);
}
