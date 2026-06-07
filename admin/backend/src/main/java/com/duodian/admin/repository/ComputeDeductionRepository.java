package com.duodian.admin.repository;

import com.duodian.admin.entity.ComputeDeduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComputeDeductionRepository extends JpaRepository<ComputeDeduction, Long> {
    Optional<ComputeDeduction> findByUserIdAndCloneInstanceIdAndDeductionTypeAndOperationKeyAndDeleted(
            Long userId,
            String cloneInstanceId,
            String deductionType,
            String operationKey,
            Byte deleted
    );

    Optional<ComputeDeduction> findFirstByUserIdAndDeductionTypeAndOperationKeyAndDeleted(
            Long userId,
            String deductionType,
            String operationKey,
            Byte deleted
    );
}
