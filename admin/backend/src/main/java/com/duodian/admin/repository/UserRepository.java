package com.duodian.admin.repository;

import com.duodian.admin.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByDeleted(Byte deleted);
    Page<User> findByDeleted(Byte deleted, Pageable pageable);
    Optional<User> findByIdAndDeleted(Long id, Byte deleted);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockByIdAndDeleted(Long id, Byte deleted);
    Optional<User> findByPhoneAndDeleted(String phone, Byte deleted);
    boolean existsByPhoneAndDeleted(String phone, Byte deleted);

    @Query("""
            select u
            from User u
            where u.deleted = :active
              and (:username is null or u.username like concat('%', :username, '%'))
              and (:phone is null or u.phone like concat('%', :phone, '%'))
              and (:role is null or u.role = :role)
            """)
    Page<User> searchUsers(
            @Param("active") Byte active,
            @Param("username") String username,
            @Param("phone") String phone,
            @Param("role") String role,
            Pageable pageable
    );
}
