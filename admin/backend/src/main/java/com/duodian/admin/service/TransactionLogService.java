package com.duodian.admin.service;

import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.repository.TransactionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionLogService {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final TransactionLogRepository logRepository;

    public TransactionLogService(TransactionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public List<TransactionLog> findAll() {
        return logRepository.findByDeletedOrderByCreatedAtDesc(ACTIVE);
    }

    public Optional<TransactionLog> findById(Long id) {
        return logRepository.findByIdAndDeleted(id, ACTIVE);
    }

    public List<TransactionLog> findByUserId(Long userId) {
        return logRepository.findByUserIdAndDeletedOrderByCreatedAtDesc(userId, ACTIVE);
    }

    public List<TransactionLog> findByType(String type) {
        return logRepository.findByTypeAndDeletedOrderByCreatedAtDesc(type, ACTIVE);
    }

    public Page<TransactionLog> findByUserIdAndConditions(Long userId, String type,
                                                           LocalDateTime startDate, LocalDateTime endDate,
                                                           int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return logRepository.findByUserIdAndConditions(userId, ACTIVE, type, startDate, endDate, pageable);
    }

    public TransactionLog create(TransactionLog log) {
        log.setDeleted(ACTIVE);
        return logRepository.save(log);
    }

    public void delete(Long id) {
        TransactionLog log = logRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("日志不存在"));
        log.setDeleted(DELETED);
        logRepository.save(log);
    }
}
