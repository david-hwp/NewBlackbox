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
    List<Shop> findByUserIdAndPackageName(Long userId, String packageName);
    List<Shop> findByPackageName(String packageName);
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
            select count(distinct s.packageName)
            from Shop s
            where s.userId = :userId
              and s.shopId is not null
              and s.shopId not like 'NEW-%'
              and s.packageName is not null
              and s.packageName <> ''
            """)
    long countRealPlatformsByUserId(@Param("userId") Long userId);

    java.util.Optional<Shop> findByUserIdAndShopId(Long userId, String shopId);
    java.util.Optional<Shop> findByUserIdAndShopIdAndPackageName(Long userId, String shopId, String packageName);
    java.util.Optional<Shop> findByUserIdAndCloneInstanceId(Long userId, String cloneInstanceId);
    java.util.Optional<Shop> findFirstByUserIdAndPackageNameAndShopIdStartingWith(
            Long userId,
            String packageName,
            String shopIdPrefix
    );
    boolean existsByUserIdAndPackageNameAndShopIdStartingWith(Long userId, String packageName, String shopIdPrefix);
}
