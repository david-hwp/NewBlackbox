package com.duodian.admin.repository;

import com.duodian.admin.entity.AdvancedFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdvancedFeatureRepository extends JpaRepository<AdvancedFeature, Long> {
    List<AdvancedFeature> findByDeletedOrderBySortOrderAscIdAsc(Byte deleted);
    Optional<AdvancedFeature> findByCodeAndDeleted(String code, Byte deleted);
}
