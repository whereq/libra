package com.whereq.libra.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration class for Apache Spark.
 * Provides SparkSession bean and configuration properties.
 *
 * @author WhereQ Inc.
 */
@Configuration
@ConfigurationProperties(prefix = "spark")
@Data
@Slf4j
public class SparkConfig {

    private String appName = "whereq-libra";
    private String master = "local[*]";
    private Map<String, String> config;

    @Bean
    public SparkConf sparkConf() {
        SparkConf conf = new SparkConf()
                .setAppName(appName)
                .setMaster(master);

        if (config != null) {
            config.forEach(conf::set);
        }

        return conf;
    }

    // Note: SparkSession creation is now handled by SparkSessionManager
    // This allows for flexible session management (SHARED or ISOLATED mode)
    // The SparkConf bean is still needed for configuration
}
