package com.kai.douyin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Des:
 * Author: yyk
 * Date: 2026/2/26
 **/
@SpringBootApplication
@EnableScheduling
@MapperScan("com.kai.douyin.mapper")
public class DouyinCommentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DouyinCommentApplication.class, args);
    }
}