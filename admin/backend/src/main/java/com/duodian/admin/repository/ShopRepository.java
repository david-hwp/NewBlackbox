package com.duodian.admin.repository;

import com.duodian.admin.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByDeleted(Byte deleted);
    java.util.Optional<Shop> findByIdAndDeleted(Long id, Byte deleted);
    List<Shop> findByUserIdAndDeleted(Long userId, Byte deleted);
    List<Shop> findByUserIdAndPackageNameAndDeleted(Long userId, String packageName, Byte deleted);
    List<Shop> findByPackageNameAndDeleted(String packageName, Byte deleted);
    long countByUserIdAndDeleted(Long userId, Byte deleted);

    @Query("""
            select count(s)
            from Shop s
            where s.userId = :userId
              and s.deleted = :active
              and s.shopId is not null
              and s.shopId not like 'NEW-%'
            """)
    long countRealShopsByUserId(@Param("userId") Long userId, @Param("active") Byte active);

    @Query("""
            select count(distinct s.packageName)
            from Shop s
            where s.userId = :userId
              and s.deleted = :active
              and s.shopId is not null
              and s.shopId not like 'NEW-%'
              and s.packageName is not null
              and s.packageName <> ''
            """)
    long countRealPlatformsByUserId(@Param("userId") Long userId, @Param("active") Byte active);

    java.util.Optional<Shop> findByUserIdAndShopIdAndDeleted(Long userId, String shopId, Byte deleted);
    java.util.Optional<Shop> findByUserIdAndShopIdAndPackageNameAndDeleted(Long userId, String shopId, String packageName, Byte deleted);
    java.util.Optional<Shop> findByUserIdAndCloneInstanceIdAndDeleted(Long userId, String cloneInstanceId, Byte deleted);
    java.util.Optional<Shop> findFirstByUserIdAndPackageNameAndShopIdStartingWithAndDeleted(
            Long userId,
            String packageName,
            String shopIdPrefix,
            Byte deleted
    );
    boolean existsByUserIdAndPackageNameAndShopIdStartingWithAndDeleted(Long userId, String packageName, String shopIdPrefix, Byte deleted);
}
