package com.java2nb.novel;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.net.InetAddress;

/**
 * @author Administrator
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@EnableCaching
@ServletComponentScan
@MapperScan(basePackages = {"com.java2nb.novel.mapper", "com.java2nb.novel.common.dao"})
@Slf4j
public class FrontNovelApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontNovelApplication.class);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            log.info("Ứng dụng đã khởi động, địa chỉ truy cập: {}",
                "http://" + InetAddress.getLocalHost().getHostAddress() + ":" + ctx.getEnvironment()
                    .getProperty("server.port"));
        };
    }

    /**
     * Bảo đảm tại một thời điểm chỉ một tác vụ theo lịch được thực thi
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        return taskScheduler;
    }
}
