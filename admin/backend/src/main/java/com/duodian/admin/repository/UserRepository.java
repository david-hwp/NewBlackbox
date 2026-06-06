package com.duodian.admin.repository;

import com.duodian.admin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByDeleted(Byte deleted);
    Optional<User> findByIdAndDeleted(Long id, Byte deleted);
    Optional<User> findByPhoneAndDeleted(String phone, Byte deleted);
    boolean existsByPhoneAndDeleted(String phone, Byte deleted);
}
