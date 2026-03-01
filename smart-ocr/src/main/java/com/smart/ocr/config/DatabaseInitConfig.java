package com.smart.ocr.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * SQLite数据库初始化配置
 */
@Slf4j
@Configuration
public class DatabaseInitConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @PostConstruct
    public void initDatabase() {
        try {
            // 确保数据目录存在
            String dbPath = jdbcUrl.replace("jdbc:sqlite:", "");
            Path dataDir = Paths.get(dbPath).getParent();
            if (dataDir != null && !Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                log.info("创建数据目录: {}", dataDir);
            }

            // 执行初始化SQL
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 Statement stmt = conn.createStatement()) {
                
                // 读取初始化SQL脚本
                InputStream is = getClass().getResourceAsStream("/db/init.sql");
                if (is != null) {
                    String sql = new BufferedReader(new InputStreamReader(is))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    
                    // 分割并执行每条SQL语句
                    for (String statement : sql.split(";")) {
                        String trimmed = statement.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                            stmt.execute(trimmed);
                        }
                    }
                    log.info("数据库初始化完成");
                }
            }
        } catch (Exception e) {
            log.error("数据库初始化失败", e);
        }
    }
}
