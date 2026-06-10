package com.example.validator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 迁移核验程序启动类。
 *
 * <p>职责：启动 Spring Boot 容器，加载配置属性，并扫描 MyBatis Plus Mapper。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.example.validator.mapper")
public class ValidatorApplication {
    /**
     * 程序入口。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ValidatorApplication.class, args);
    }
}
