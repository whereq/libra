package com.example.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.Arrays;
import java.util.List;

/**
 * Sample Spark application that demonstrates SparkSession.getOrCreate().
 *
 * KEY CONCEPT:
 * - When executed via Libra's "jar-class" kind, this application runs in Libra's JVM
 * - The getOrCreate() call will find and reuse Libra's existing SparkSession
 * - NO CODE CHANGES NEEDED - your existing Spark apps work as-is!
 *
 * Author: WhereQ Inc.
 */
public class SimpleSparkApp {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Simple Spark Application - WhereQ Libra Demo");
        System.out.println("=".repeat(60));

        // This will find the existing SparkSession created by Libra!
        // NO NEED to create a new one - getOrCreate() returns existing session
        SparkSession spark = SparkSession.builder()
                .appName("SimpleSparkApp")
                .getOrCreate();

        System.out.println("\nSparkSession Information:");
        System.out.println("  Spark Version: " + spark.version());
        System.out.println("  Application ID: " + spark.sparkContext().applicationId());
        System.out.println("  Master: " + spark.sparkContext().master());
        System.out.println("  App Name: " + spark.sparkContext().appName());

        // Print command-line arguments if provided
        if (args.length > 0) {
            System.out.println("\nCommand-line Arguments:");
            for (int i = 0; i < args.length; i++) {
                System.out.println("  Arg[" + i + "]: " + args[i]);
            }
        }

        // Create sample data
        List<Person> people = Arrays.asList(
                new Person("Alice", 25, "Engineer"),
                new Person("Bob", 30, "Manager"),
                new Person("Charlie", 35, "Director"),
                new Person("Diana", 28, "Engineer"),
                new Person("Eve", 32, "Manager")
        );

        // Create DataFrame
        Dataset<Row> df = spark.createDataFrame(people, Person.class);

        System.out.println("\nOriginal DataFrame:");
        df.show();

        // Perform some transformations
        System.out.println("\nEngineers only:");
        df.filter("role = 'Engineer'").show();

        System.out.println("\nAverage age by role:");
        df.groupBy("role").avg("age").show();

        // Count records
        long count = df.count();
        System.out.println("\nTotal records: " + count);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Application completed successfully!");
        System.out.println("=".repeat(60));

        // IMPORTANT: Do NOT call spark.stop() when using "jar-class" kind
        // The SparkSession is shared with Libra and other jobs
        // Stopping it would affect other jobs!

        // NOTE: If using "jar" kind (spark-submit), you CAN call spark.stop()
        // because it runs in a separate process with its own SparkSession
    }

    public static class Person implements java.io.Serializable {
        private String name;
        private int age;
        private String role;

        public Person() {}

        public Person(String name, int age, String role) {
            this.name = name;
            this.age = age;
            this.role = role;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
