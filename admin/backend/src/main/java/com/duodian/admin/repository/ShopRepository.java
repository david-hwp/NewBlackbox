package com.duodian.admin.repository;

import com.duodian.admin.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByDeleted(Byte deleted);
    java.util.Optional<Shop> findByIdAndDeleted(Long id, Byte deleted);
    List<Shop> findByChannelIdAndDeleted(Long channelId, Byte deleted);
    List<Shop> findByUserIdAndDeletedOrderByCardSortOrderAscCreatedAtDescIdDesc(Long userId, Byte deleted);
    List<Shop> findByUserIdAndPackageNameAndDeletedOrderByCardSortOrderAscCreatedAtDescIdDesc(Long userId, String packageName, Byte deleted);
    List<Shop> findByIdInAndDeleted(Collection<Long> ids, Byte deleted);
    List<Shop> findByPackageNameAndDeleted(String packageName, Byte deleted);
    @Query("""
            select s
            from Shop s
            where s.deleted = :active
              and upper(s.shopAuthorizationStatus) = 'AUTHORIZED'
              and s.platform in :platforms
            order by s.id asc
            """)
    List<Shop> findAuthorizedOrderCrawlTargets(
            @Param("active") Byte active,
            @Param("platforms") Collection<String> platforms
    );
    @Query("""
            select s
            from Shop s
            where s.deleted = :active
              and (s.expireAt is not null or s.authExpireAt is not null)
            """)
    List<Shop> findActiveShopsWithExpiration(@Param("active") Byte active);
    long countByUserIdAndDeleted(Long userId, Byte deleted);
    long countByUserIdAndPackageNameAndDeleted(Long userId, String packageName, Byte deleted);

    java.util.Optional<Shop> findByUserIdAndShopIdAndDeleted(Long userId, String shopId, Byte deleted);
    java.util.Optional<Shop> findByUserIdAndShopIdAndPackageNameAndDeleted(Long userId, String shopId, String packageName, Byte deleted);
    java.util.Optional<Shop> findByCloneInstanceIdAndDeleted(String cloneInstanceId, Byte deleted);
    java.util.Optional<Shop> findByUserIdAndCloneInstanceIdAndDeleted(Long userId, String cloneInstanceId, Byte deleted);
    java.util.Optional<Shop> findFirstByUserIdAndPackageNameAndShopIdStartingWithAndDeleted(
            Long userId,
            String packageName,
            String shopIdPrefix,
            Byte deleted
    );
    @Query("""
            select s
            from Shop s
            left join User u on u.id = s.userId and u.deleted = :active
            where s.deleted = :active
              and (:userId is null or s.userId = :userId)
              and (:channelId is null or s.channelId = :channelId)
              and (:packageName is null or s.packageName = :packageName)
              and (:platform is null or s.platform = :platform)
              and (:phone is null or u.phone like concat('%', :phone, '%'))
              and (:userKeyword is null or cast(s.userId as string) like concat('%', :userKeyword, '%')
                   or u.username like concat('%', :userKeyword, '%')
                   or u.phone like concat('%', :userKeyword, '%'))
              and (:shopName is null or s.shopName like concat('%', :shopName, '%'))
            order by s.createdAt desc
            """)
    Page<Shop> searchShops(
            @Param("active") Byte active,
            @Param("userId") Long userId,
            @Param("channelId") Long channelId,
            @Param("packageName") String packageName,
            @Param("platform") String platform,
            @Param("phone") String phone,
            @Param("userKeyword") String userKeyword,
            @Param("shopName") String shopName,
            Pageable pageable
    );
}
