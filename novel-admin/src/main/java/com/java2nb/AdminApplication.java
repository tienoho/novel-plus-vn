package com.java2nb;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.sql.Connection;


@EnableTransactionManagement
@ServletComponentScan
@MapperScan(basePackages = {
    "com.java2nb.common.dao",
    "com.java2nb.novel.dao",
    "com.java2nb.system.dao",
    "com.java2nb.novel.common.dao",
    "com.java2nb.novel.mapper"
})
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@EnableCaching
@Slf4j
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx, DataSource dataSource) {
        return args -> {
            log.info("Đang tạo nhóm kết nối...");
            try (Connection connection = dataSource.getConnection()) {
                log.info("Đã tạo nhóm kết nối.");
                log.info("Cơ sở dữ liệu: {}", connection.getMetaData().getDatabaseProductName());
                log.info("Phiên bản cơ sở dữ liệu: {}", connection.getMetaData().getDatabaseProductVersion());
            }
            log.info("Ứng dụng đã khởi động, địa chỉ truy cập: {}",
                "http://" + InetAddress.getLocalHost().getHostAddress() + ":" + ctx.getEnvironment()
                    .getProperty("server.port"));
        };
    }
}
