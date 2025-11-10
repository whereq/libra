package com.whereq.libra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for WhereQ Libra.
 * This service provides a REST interface for Apache Spark cluster operations,
 * serving as an alternative to Apache Livy.
 *
 * @author WhereQ Inc.
 */
@SpringBootApplication
public class LibraApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraApplication.class, args);
    }
}
