package com.dev.gateway.ratelimit.repository;

import com.dev.gateway.ratelimit.model.RateLimitLogEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 限流日志Repository接口
 * 
 * @author 系统
 * @version 1.0
 */
@Repository
public interface RateLimitLogRepository extends ReactiveMongoRepository<RateLimitLogEntity, String> {

    /**
     * 根据IP地址查询限流日志
     * 
     * @param clientIp  客户端IP
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 限流日志列表
     */
    Flux<RateLimitLogEntity> findByClientIpAndTriggerTimeBetween(String clientIp, LocalDateTime startTime,
            LocalDateTime endTime);

    /**
     * 根据限流类型查询日志
     * 
     * @param rateLimitType 限流类型
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 限流日志列表
     */
    Flux<RateLimitLogEntity> findByRateLimitTypeAndTriggerTimeBetween(String rateLimitType, LocalDateTime startTime,
            LocalDateTime endTime);

    /**
     * 统计指定时间范围内的限流次数
     * 
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 限流次数
     */
    Mono<Long> countByTriggerTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询DDoS攻击日志
     * 
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return DDoS攻击日志列表
     */
    Flux<RateLimitLogEntity> findByIsDdosAttackTrueAndTriggerTimeBetween(LocalDateTime startTime,
            LocalDateTime endTime);

    /**
     * 根据IP统计限流次数
     * 
     * @param clientIp  客户端IP
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 限流次数
     */
    Mono<Long> countByClientIpAndTriggerTimeBetween(String clientIp, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 清理指定时间之前的日志
     * 
     * @param beforeTime 清理时间点
     * @return 删除的记录数量
     */
    @Query(value = "{'triggerTime': {$lt: ?0}}", delete = true)
    Mono<Long> deleteByTriggerTimeBefore(LocalDateTime beforeTime);
}