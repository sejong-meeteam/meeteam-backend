package com.wardk.meeteam_backend.global.config;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.jsr107.Eh107Configuration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;

import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CachingProvider provider = Caching.getCachingProvider();
        javax.cache.CacheManager cacheManager = provider.getCacheManager();

        // mainPageProjects 캐시 생성
        cacheManager.createCache("mainPageProjects", mainPageProjectsCache());

        return new JCacheCacheManager(cacheManager);
    }

    private javax.cache.configuration.Configuration<String, Page> mainPageProjectsCache() {
        return Eh107Configuration.fromEhcacheCacheConfiguration(
                CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(
                                String.class, Page.class,
                                ResourcePoolsBuilder.heap(10)  // 제한적인 캐시 조건으로 인해 다시 축소
                                        .offheap(5, MemoryUnit.MB)  // offheap도 축소
                        )
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5)))
                        .build()
        );
    }
}