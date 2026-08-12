package com.filemngt.v2.query.adapter.out.cache;

import com.filemngt.v2.query.application.QueryDetailCache;
import com.filemngt.v2.query.application.QuerySubjectDetail;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisQueryDetailCache implements QueryDetailCache {
    private static final Logger log = LoggerFactory.getLogger(RedisQueryDetailCache.class);
    private static final String KEY_PREFIX = "query:subject-detail:v2:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;
    private final boolean enabled;
    private final Counter hit;
    private final Counter miss;
    private final Counter put;
    private final Counter eviction;
    private final Counter error;
    private final Timer lookupLatency;

    public RedisQueryDetailCache(
            StringRedisTemplate redis,
            ObjectMapper json,
            MeterRegistry meterRegistry,
            @Value("${query.detail-cache.ttl:10m}") Duration ttl,
            @Value("${query.detail-cache.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
        this.enabled = enabled;
        Assert.isTrue(!ttl.isZero() && !ttl.isNegative(), "query.detail-cache.ttl must be positive");
        hit = meterRegistry.counter("query.detail.cache.hit");
        miss = meterRegistry.counter("query.detail.cache.miss");
        put = meterRegistry.counter("query.detail.cache.put");
        eviction = meterRegistry.counter("query.detail.cache.eviction");
        error = meterRegistry.counter("query.detail.cache.error");
        lookupLatency = meterRegistry.timer("query.detail.cache.lookup");
    }

    @Override
    public Optional<QuerySubjectDetail> get(UUID subjectId) {
        if (!enabled) return Optional.empty();
        return lookupLatency.record(() -> read(subjectId));
    }

    @Override
    public void put(QuerySubjectDetail detail) {
        if (!enabled) return;
        try {
            var value = json.writeValueAsString(QuerySubjectCacheEntry.from(detail));
            redis.opsForValue().set(key(detail.id()), value, ttl);
            put.increment();
        } catch (DataAccessException | JacksonException exception) {
            recordFailure("put", detail.id(), exception);
        }
    }

    @Override
    public void evict(UUID subjectId) {
        if (!enabled) return;
        try {
            if (Boolean.TRUE.equals(redis.delete(key(subjectId)))) eviction.increment();
        } catch (DataAccessException exception) {
            recordFailure("evict", subjectId, exception);
        }
    }

    private Optional<QuerySubjectDetail> read(UUID subjectId) {
        try {
            var value = redis.opsForValue().get(key(subjectId));
            if (value == null) {
                miss.increment();
                return Optional.empty();
            }
            var detail = json.readValue(value, QuerySubjectCacheEntry.class).toDetail();
            hit.increment();
            return Optional.of(detail);
        } catch (DataAccessException | JacksonException exception) {
            error.increment();
            miss.increment();
            deleteCorruptEntry(subjectId, exception);
            log.debug("Redis detail cache get failed for {}", subjectId, exception);
            return Optional.empty();
        }
    }

    private void deleteCorruptEntry(UUID subjectId, Exception cause) {
        if (!(cause instanceof JacksonException)) return;
        try {
            if (Boolean.TRUE.equals(redis.delete(key(subjectId)))) eviction.increment();
        } catch (DataAccessException deleteFailure) {
            log.debug("Redis detail cache corrupt entry cleanup failed for {}", subjectId, deleteFailure);
        }
    }

    private void recordFailure(String operation, UUID subjectId, Exception exception) {
        error.increment();
        log.debug("Redis detail cache {} failed for {}", operation, subjectId, exception);
    }

    static String key(UUID subjectId) {
        return KEY_PREFIX + subjectId;
    }
}
