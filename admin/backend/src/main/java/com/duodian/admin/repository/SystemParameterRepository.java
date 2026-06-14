package com.duodian.admin.repository;

import com.duodian.admin.entity.SystemParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemParameterRepository extends JpaRepository<SystemParameter, Long> {
    List<SystemParameter> findByDeletedOrderByUpdatedAtDesc(Byte deleted);
    List<SystemParameter> findByChannelIdAndDeletedOrderByUpdatedAtDesc(Long channelId, Byte deleted);
    List<SystemParameter> findByChannelIdInAndDeletedOrderByUpdatedAtDesc(List<Long> channelIds, Byte deleted);
    Optional<SystemParameter> findByIdAndDeleted(Long id, Byte deleted);
    Optional<SystemParameter> findFirstByChannelIdAndCodeAndDeleted(Long channelId, String code, Byte deleted);
    boolean existsByChannelIdAndCodeAndDeleted(Long channelId, String code, Byte deleted);
}
