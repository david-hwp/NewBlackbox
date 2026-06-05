package com.duodian.admin.repository;

import com.duodian.admin.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByUserId(Long userId);
    List<Shop> findByPlatform(String platform);
    long countByUserId(Long userId);
    java.util.Optional<Shop> findByUserIdAndShopId(Long userId, String shopId);
    java.util.Optional<Shop> findByUserIdAndCloneInstanceId(Long userId, String cloneInstanceId);
    java.util.Optional<Shop> findByUserIdAndPackageNameAndPlatform(Long userId, String packageName, String platform);
    java.util.Optional<Shop> findFirstByUserIdAndPackageNameAndPlatformAndShopIdStartingWith(
            Long userId,
            String packageName,
            String platform,
            String shopIdPrefix
    );
    boolean existsByUserIdAndPlatformAndShopIdStartingWith(Long userId, String platform, String shopIdPrefix);
}
