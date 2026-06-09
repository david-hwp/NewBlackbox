package com.duodian.admin.repository;

import com.duodian.admin.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByDeletedOrderByCreatedAtDesc(Byte deleted);
    Optional<Channel> findByIdAndDeleted(Long id, Byte deleted);
    Optional<Channel> findByCodeAndDeleted(String code, Byte deleted);
    boolean existsByCodeAndDeleted(String code, Byte deleted);
}
