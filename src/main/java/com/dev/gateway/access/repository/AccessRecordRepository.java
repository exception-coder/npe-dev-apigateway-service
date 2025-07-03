package com.dev.gateway.access.repository;

import com.dev.gateway.access.model.AccessRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 访问记录Repository接口
 * 提供MongoDB的响应式数据访问
 */
@Repository
public interface AccessRecordRepository extends ReactiveMongoRepository<AccessRecord, String> {

    /**
     * 根据客户端IP查找访问记录
     * @param clientIp 客户端IP
     * @param pageable 分页参数
     * @return 访问记录流
     */
    Flux<AccessRecord> findByClientIpOrderByAccessTimeDesc(String clientIp, Pageable pageable);

    /**
     * 查找指定时间范围内的访问记录
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页参数
     * @return 访问记录流
     */
    Flux<AccessRecord> findByAccessTimeBetweenOrderByAccessTimeDesc(
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 查找被限流的访问记录
     * @param rateLimited 是否被限流
     * @param pageable 分页参数
     * @return 访问记录流
     */
    Flux<AccessRecord> findByRateLimitedOrderByAccessTimeDesc(Boolean rateLimited, Pageable pageable);

    /**
     * 根据IP和时间范围统计访问次数
     * @param clientIp 客户端IP
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 访问次数
     */
    @Query("{ 'clientIp': ?0, 'accessTime': { $gte: ?1, $lte: ?2 } }")
    Mono<Long> countByClientIpAndAccessTimeBetween(String clientIp, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定时间范围内被限流的访问次数
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 被限流的访问次数
     */
    @Query("{ 'rateLimited': true, 'accessTime': { $gte: ?0, $lte: ?1 } }")
    Mono<Long> countRateLimitedBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定时间范围内的总访问次数
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 总访问次数
     */
    @Query("{ 'accessTime': { $gte: ?0, $lte: ?1 } }")
    Mono<Long> countByAccessTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定时间范围内的活跃IP数量
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 活跃IP数量
     */
    @Query(value = "{ 'accessTime': { $gte: ?0, $lte: ?1 } }", count = true)
    Mono<Long> countDistinctClientIpByAccessTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据请求路径和时间范围查找访问记录
     * @param requestPath 请求路径
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页参数
     * @return 访问记录流
     */
    Flux<AccessRecord> findByRequestPathAndAccessTimeBetweenOrderByAccessTimeDesc(
            String requestPath, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 删除指定时间之前的访问记录（用于数据清理）
     * @param beforeTime 指定时间
     * @return 删除的记录数量
     */
    Mono<Long> deleteByAccessTimeBefore(LocalDateTime beforeTime);

    /**
     * 查找高频访问的IP地址
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param minCount 最小访问次数
     * @return 高频访问的访问记录
     */
    @Query("{ 'accessTime': { $gte: ?0, $lte: ?1 } }")
    Flux<AccessRecord> findHighFrequencyIps(LocalDateTime startTime, LocalDateTime endTime, Long minCount);

    /**
     * 根据地理位置查找访问记录
     * @param country 国家
     * @param pageable 分页参数
     * @return 访问记录流
     */
    @Query("{ 'geoLocation.country': ?0 }")
    Flux<AccessRecord> findByGeoLocationCountry(String country, Pageable pageable);
} 