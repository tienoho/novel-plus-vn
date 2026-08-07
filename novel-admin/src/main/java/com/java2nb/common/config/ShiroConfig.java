package com.java2nb.common.config;

import at.pollux.thymeleaf.shiro.dialect.ShiroDialect;
import com.java2nb.common.redis.shiro.RedisCacheManager;
import com.java2nb.common.redis.shiro.RedisManager;
import com.java2nb.common.redis.shiro.RedisSessionDAO;
import com.java2nb.system.shiro.UserRealm;
import com.java2nb.system.shiro.PasswordChangeFilter;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.SessionListener;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.Filter;

/**
 * @author xiongxy
 */
@Configuration
public class ShiroConfig {

    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.password}")
    private String password;
    @Value("${spring.redis.port}")
    private int port;
    @Value("${spring.redis.timeout}")
    private int timeout;

    @Value("${server.session-timeout}")
    private int tomcatTimeout;

    @Bean
    public static LifecycleBeanPostProcessor getLifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }

    /**
     * Bean ShiroDialect để dùng thẻ Shiro trong Thymeleaf
     *
     * @return
     */
    @Bean
    public ShiroDialect shiroDialect() {
        return new ShiroDialect();
    }

    @Bean
    ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager) {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager);
        shiroFilterFactoryBean.setLoginUrl("/login");
        shiroFilterFactoryBean.setSuccessUrl("/index");
        shiroFilterFactoryBean.setUnauthorizedUrl("/403");
        Map<String, Filter> filters = new HashMap<>();
        filters.put("passwordChange", new PasswordChangeFilter());
        shiroFilterFactoryBean.setFilters(filters);
        LinkedHashMap<String, String> filterChainDefinitionMap = new LinkedHashMap<>();
        filterChainDefinitionMap.put("/login", "anon");
        filterChainDefinitionMap.put("/getVerify", "anon");
        filterChainDefinitionMap.put("/css/**", "anon");
        filterChainDefinitionMap.put("/js/**", "anon");
        filterChainDefinitionMap.put("/fonts/**", "anon");
        filterChainDefinitionMap.put("/img/**", "anon");
        filterChainDefinitionMap.put("/favicon.ico", "anon");
        filterChainDefinitionMap.put("/actuator/health", "anon");
        filterChainDefinitionMap.put("/actuator/prometheus", "anon");
        filterChainDefinitionMap.put("/docs/**", "anon");
        filterChainDefinitionMap.put("/layuimini/**", "anon");
        filterChainDefinitionMap.put("/upload/**", "anon");
        filterChainDefinitionMap.put(Constant.UPLOAD_FILES_PREFIX + "**", "anon");
        filterChainDefinitionMap.put("/logout", "logout");
        filterChainDefinitionMap.put("/blog", "anon");
        filterChainDefinitionMap.put("/blog/open/**", "anon");
        filterChainDefinitionMap.put("/**", "authc,passwordChange");
        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return shiroFilterFactoryBean;
    }


    @Bean
    public SecurityManager securityManager() {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        // Cấu hình realm.
        securityManager.setRealm(userRealm());
        // Triển khai bộ nhớ đệm tùy chỉnh bằng Redis
        securityManager.setCacheManager(rediscacheManager());
        securityManager.setSessionManager(sessionManager());
        return securityManager;
    }

    @Bean
    UserRealm userRealm() {
        UserRealm userRealm = new UserRealm();
        return userRealm;
    }

    /**
     * Bật hỗ trợ annotation AOP của Shiro bằng cơ chế proxy.
     *
     * @param securityManager
     * @return
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor = new AuthorizationAttributeSourceAdvisor();
        authorizationAttributeSourceAdvisor.setSecurityManager(securityManager);
        return authorizationAttributeSourceAdvisor;
    }

    /**
     * Cấu hình redisManager cho Shiro
     *
     * @return
     */
    @Bean
    public RedisManager redisManager() {
        RedisManager redisManager = new RedisManager();
        redisManager.setHost(host);
        redisManager.setPort(port);
        redisManager.setExpire(1800);// Cấu hình thời gian hết hạn bộ nhớ đệm
        //redisManager.setTimeout(1800);
        redisManager.setPassword(password);
        return redisManager;
    }

    /**
     * CacheManager dùng Redis thông qua plugin nguồn mở shiro-redis
     *
     * @return
     */
    public RedisCacheManager rediscacheManager() {
        RedisCacheManager redisCacheManager = new RedisCacheManager();
        redisCacheManager.setRedisManager(redisManager());
        return redisCacheManager;
    }


    /**
     * RedisSessionDAO triển khai lớp sessionDao của Shiro qua Redis bằng plugin shiro-redis
     */
    @Bean
    public RedisSessionDAO redisSessionDAO() {
        RedisSessionDAO redisSessionDAO = new RedisSessionDAO();
        redisSessionDAO.setRedisManager(redisManager());
        return redisSessionDAO;
    }

    @Bean
    public SessionDAO sessionDAO() {
        return redisSessionDAO();
    }

    /**
     * Quản lý session Shiro
     */
    @Bean
    public DefaultWebSessionManager sessionManager() {
        DefaultWebSessionManager sessionManager = new DefaultWebSessionManager();
        sessionManager.setGlobalSessionTimeout(tomcatTimeout * 1000);
        sessionManager.setSessionDAO(sessionDAO());
        Collection<SessionListener> listeners = new ArrayList<SessionListener>();
        listeners.add(new BDSessionListener());
        sessionManager.setSessionListeners(listeners);
        return sessionManager;
    }

}
