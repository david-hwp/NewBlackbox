package com.duodian.admin.repository;

import com.duodian.admin.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByUserId(Long userId);
    List<Shop> findByUserIdAndPlatform(Long userId, String platform);
    List<Shop> findByPlatform(String platform);
    long countByUserId(Long userId);
    @Query("""
            select count(s)
            from Shop s
            where s.userId = :userId
              and s.shopId is not null
              and s.shopId not like 'NEW-%'
            """)
    long countRealShopsByUserId(@Param("userId") Long userId);

    @Query("""
            select count(distinct s.platform)
            from Shop s
            where s.userId = :userId
              and s.shopId is not null
              and s.shopId not like 'NEW-%'
              and s.platform is not null
              and s.platform <> ''
            """)
    long countRealPlatformsByUserId(@Param("userId") Long userId);

    java.util.Optional<Shop> findByUserIdAndShopId(Long userId, String shopId);
    java.util.Optional<Shop> findByUserIdAndShopIdAndPlatform(Long userId, String shopId, String platform);
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
