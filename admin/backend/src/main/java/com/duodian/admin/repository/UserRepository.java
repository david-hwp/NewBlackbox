package com.duodian.admin.repository;

import com.duodian.admin.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByDeleted(Byte deleted);
    Optional<User> findByIdAndDeleted(Long id, Byte deleted);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockByIdAndDeleted(Long id, Byte deleted);
    Optional<User> findByPhoneAndDeleted(String phone, Byte deleted);
    boolean existsByPhoneAndDeleted(String phone, Byte deleted);
}
