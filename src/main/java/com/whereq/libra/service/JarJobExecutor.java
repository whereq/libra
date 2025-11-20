package com.whereq.libra.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.SparkConf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes Java JAR-based Spark jobs.
 *
 * Supports two execution modes:
 * 1. In-JVM execution: Loads JAR and invokes main class directly
 * 2. spark-submit execution: Runs JAR as separate process
 *
 * @author WhereQ Inc.
 */
@Component
@Slf4j
public class JarJobExecutor {

    @Autowired
    private SparkConf sparkConf;

    /**
     * Execute JAR main class in-JVM reactively (recommended).
     * The application's SparkSession.getOrCreate() will find the existing SparkSession.
     *
     * @param jarPath Path to JAR file
     * @param mainClass Fully qualified main class name (e.g., com.example.MySparkApp)
     * @param args Application arguments
     * @return Mono of execution output
     */
    public Mono<String> executeJarInJVMReactive(String jarPath, String mainClass, String[] args) {
        return Mono.fromCallable(() -> executeJarInJVM(jarPath, mainClass, args))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Execute JAR main class in-JVM (recommended).
     * The application's SparkSession.getOrCreate() will find the existing SparkSession.
     *
     * @param jarPath Path to JAR file
     * @param mainClass Fully qualified main class name (e.g., com.example.MySparkApp)
     * @param args Application arguments
     * @return Execution output
     */
    public String executeJarInJVM(String jarPath, String mainClass, String[] args) throws Exception {
        log.info("Executing JAR in-JVM: {} with main class: {}", jarPath, mainClass);

        File jarFile = resolveJarPath(jarPath);
        if (!jarFile.exists()) {
            throw new FileNotFoundException("JAR file not found: " + jarPath);
        }

        // Capture System.out and System.err
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            // Set up output capture
            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));

            // Load JAR into classloader
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                Thread.currentThread().getContextClassLoader()
            );

            // Load the main class
            Class<?> clazz = classLoader.loadClass(mainClass);

            // Find main method
            Method mainMethod = clazz.getMethod("main", String[].class);

            log.info("Invoking main method with args: {}", args != null ? String.join(", ", args) : "none");

            // Set context classloader
            Thread.currentThread().setContextClassLoader(classLoader);

            // Invoke main method
            Object[] methodArgs = {args != null ? args : new String[0]};
            mainMethod.invoke(null, methodArgs);

            log.info("JAR execution completed successfully");

            // Get captured output
            String stdout = outContent.toString();
            String stderr = errContent.toString();

            StringBuilder result = new StringBuilder();
            if (!stdout.isEmpty()) {
                result.append("=== STDOUT ===\n").append(stdout).append("\n");
            }
            if (!stderr.isEmpty()) {
                result.append("=== STDERR ===\n").append(stderr).append("\n");
            }

            return result.toString();

        } finally {
            // Restore System.out and System.err
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /**
     * Execute JAR using spark-submit reactively (separate process).
     * Creates its own SparkSession in a separate JVM.
     *
     * @param jarPath Path to JAR file
     * @param mainClass Fully qualified main class name
     * @param args Application arguments
     * @param pool Scheduler pool
     * @param sparkConfig Per-job Spark configuration overrides
     * @return Mono of execution output
     */
    public Mono<String> executeJarWithSparkSubmitReactive(String jarPath, String mainClass, String[] args, String pool, java.util.Map<String, String> sparkConfig) {
        return Mono.fromCallable(() -> executeJarWithSparkSubmit(jarPath, mainClass, args, pool, sparkConfig))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Execute JAR using spark-submit (separate process).
     * Creates its own SparkSession in a separate JVM.
     *
     * @param jarPath Path to JAR file
     * @param mainClass Fully qualified main class name
     * @param args Application arguments
     * @param pool Scheduler pool
     * @param sparkConfig Per-job Spark configuration overrides
     * @return Execution output
     */
    public String executeJarWithSparkSubmit(String jarPath, String mainClass, String[] args, String pool, java.util.Map<String, String> sparkConfig) throws Exception {
        log.info("Executing JAR with spark-submit: {} with main class: {}", jarPath, mainClass);

        File jarFile = resolveJarPath(jarPath);
        if (!jarFile.exists()) {
            throw new FileNotFoundException("JAR file not found: " + jarPath);
        }

        // Build spark-submit command
        List<String> command = buildSparkSubmitCommand(jarFile, mainClass, args, pool, sparkConfig);

        log.info("Executing command: {}", String.join(" ", command));

        // Execute spark-submit
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("Spark output: {}", line);
            }
        }

        // Wait for completion (with timeout)
        boolean completed = process.waitFor(15, TimeUnit.MINUTES);
        if (!completed) {
            process.destroy();
            throw new RuntimeException("JAR execution timed out after 15 minutes");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("JAR execution failed with exit code: " + exitCode + "\n" + output);
        }

        log.info("JAR execution completed successfully");
        return output.toString();
    }

    /**
     * Resolve JAR file path.
     */
    private File resolveJarPath(String jarPath) {
        // Handle absolute paths
        File file = new File(jarPath);
        if (file.isAbsolute()) {
            return file;
        }

        // Handle relative paths (relative to working directory)
        return Paths.get(System.getProperty("user.dir"), jarPath).toFile();
    }

    /**
     * Build spark-submit command for JAR execution.
     */
    private List<String> buildSparkSubmitCommand(File jarFile, String mainClass, String[] args, String pool, java.util.Map<String, String> jobSparkConfig) {
        List<String> command = new ArrayList<>();

        // Find spark-submit executable
        String sparkSubmit = findSparkSubmit();
        command.add(sparkSubmit);

        // Add master
        String master = sparkConf.get("spark.master", "local[*]");
        command.add("--master");
        command.add(master);

        // Add app name
        String appName = sparkConf.get("spark.app.name", "whereq-libra") + "-jar";
        command.add("--name");
        command.add(appName);

        // Add main class
        command.add("--class");
        command.add(mainClass);

        // Determine driver memory (per-job config overrides global)
        String driverMemory = (jobSparkConfig != null && jobSparkConfig.containsKey("spark.driver.memory"))
                ? jobSparkConfig.get("spark.driver.memory")
                : sparkConf.get("spark.driver.memory", null);
        if (driverMemory != null) {
            command.add("--driver-memory");
            command.add(driverMemory);
        }

        // Determine executor memory (per-job config overrides global)
        String executorMemory = (jobSparkConfig != null && jobSparkConfig.containsKey("spark.executor.memory"))
                ? jobSparkConfig.get("spark.executor.memory")
                : sparkConf.get("spark.executor.memory", null);
        if (executorMemory != null) {
            command.add("--executor-memory");
            command.add(executorMemory);
        }

        // Determine executor cores (per-job config overrides global)
        String executorCores = (jobSparkConfig != null && jobSparkConfig.containsKey("spark.executor.cores"))
                ? jobSparkConfig.get("spark.executor.cores")
                : sparkConf.get("spark.executor.cores", null);
        if (executorCores != null) {
            command.add("--executor-cores");
            command.add(executorCores);
        }

        // Determine number of executors (per-job config overrides global)
        String numExecutors = (jobSparkConfig != null && jobSparkConfig.containsKey("spark.executor.instances"))
                ? jobSparkConfig.get("spark.executor.instances")
                : sparkConf.get("spark.executor.instances", null);
        if (numExecutors != null) {
            command.add("--num-executors");
            command.add(numExecutors);
        }

        // Determine driver cores (per-job config overrides global)
        String driverCores = (jobSparkConfig != null && jobSparkConfig.containsKey("spark.driver.cores"))
                ? jobSparkConfig.get("spark.driver.cores")
                : sparkConf.get("spark.driver.cores", null);
        if (driverCores != null) {
            command.add("--driver-cores");
            command.add(driverCores);
        }

        // Add scheduler pool
        if (pool != null && !pool.isEmpty()) {
            command.add("--conf");
            command.add("spark.scheduler.pool=" + pool);
        }

        // Add scheduler mode
        if (sparkConf.contains("spark.scheduler.mode")) {
            command.add("--conf");
            command.add("spark.scheduler.mode=" + sparkConf.get("spark.scheduler.mode"));
        }

        // Add other important configs
        if (sparkConf.contains("spark.driver.host")) {
            command.add("--conf");
            command.add("spark.driver.host=" + sparkConf.get("spark.driver.host"));
        }

        // Add all additional per-job Spark configurations
        if (jobSparkConfig != null && !jobSparkConfig.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : jobSparkConfig.entrySet()) {
                String key = entry.getKey();
                // Skip configs we've already handled with specific flags
                if (key.equals("spark.driver.memory") ||
                    key.equals("spark.executor.memory") ||
                    key.equals("spark.executor.cores") ||
                    key.equals("spark.executor.instances") ||
                    key.equals("spark.driver.cores")) {
                    continue;
                }
                command.add("--conf");
                command.add(key + "=" + entry.getValue());
            }
        }

        // Add JAR file
        command.add(jarFile.getAbsolutePath());

        // Add application arguments
        if (args != null && args.length > 0) {
            for (String arg : args) {
                command.add(arg);
            }
        }

        return command;
    }

    /**
     * Find spark-submit executable.
     */
    private String findSparkSubmit() {
        // Try SPARK_HOME environment variable
        String sparkHome = System.getenv("SPARK_HOME");
        if (sparkHome != null && !sparkHome.isEmpty()) {
            File sparkSubmit = new File(sparkHome, "bin/spark-submit");
            if (sparkSubmit.exists()) {
                return sparkSubmit.getAbsolutePath();
            }
        }

        // Try PATH
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File sparkSubmit = new File(dir, "spark-submit");
                if (sparkSubmit.exists() && sparkSubmit.canExecute()) {
                    return sparkSubmit.getAbsolutePath();
                }
            }
        }

        // Default to just "spark-submit" and hope it's in PATH
        log.warn("Could not find spark-submit in SPARK_HOME or PATH, using 'spark-submit'");
        return "spark-submit";
    }
}
