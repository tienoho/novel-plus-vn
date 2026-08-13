package com.java2nb.novel.core.cache.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.cache.CacheService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author xxy
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RedisServiceImpl implements CacheService {

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
        "local value = redis.call('GET', KEYS[1]); "
            + "if value == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0;",
        Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }


    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, String value, long timeout) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);

    }

    @Override
    public <T> T getObject(String key, Class<T> clazz) {
        String result = get(key);
        if (result != null) {
            try {
                return objectMapper.readValue(result, clazz);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @Override
    public <T> List<T> getList(String key, Class<T> clazz) {
        String result = get(key);
        if (result != null) {
            try {
                return objectMapper.readValue(result,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @Override
    public void setObject(String key, Object value) {
        if (value != null) {
            try {
                set(key, objectMapper.writeValueAsString(value));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void setObject(String key, Object value, long timeout) {
        if (value != null) {
            try {
                set(key, objectMapper.writeValueAsString(value), timeout);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void del(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Long deleted = stringRedisTemplate.execute(
            COMPARE_AND_DELETE_SCRIPT, Collections.singletonList(key), expectedValue);
        return Long.valueOf(1L).equals(deleted);
    }

    @Override
    public boolean contains(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public void expire(String key, long timeout) {
        stringRedisTemplate.expire(key, timeout, TimeUnit.SECONDS);
    }

}
