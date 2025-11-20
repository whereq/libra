package com.whereq.libra.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.SparkConf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes Python scripts as Spark jobs using spark-submit.
 *
 * @author WhereQ Inc.
 */
@Component
@Slf4j
public class PythonJobExecutor {

    @Autowired
    private SparkConf sparkConf;

    /**
     * Execute a Python script file reactively as a Spark job.
     *
     * @param filePath Path to Python script (can be absolute, relative, or classpath:)
     * @param args Additional arguments to pass to the script
     * @param pool Scheduler pool name
     * @param sparkConfig Per-job Spark configuration overrides
     * @return Mono of execution result
     */
    public Mono<String> executePythonFileReactive(String filePath, String[] args, String pool, java.util.Map<String, String> sparkConfig) {
        return Mono.fromCallable(() -> executePythonFile(filePath, args, pool, sparkConfig))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Execute a Python script file as a Spark job.
     *
     * @param filePath Path to Python script (can be absolute, relative, or classpath:)
     * @param args Additional arguments to pass to the script
     * @param pool Scheduler pool name
     * @param sparkConfig Per-job Spark configuration overrides
     * @return Execution result
     */
    public String executePythonFile(String filePath, String[] args, String pool, java.util.Map<String, String> sparkConfig) throws Exception {
        log.info("Executing Python file: {}", filePath);

        // Resolve file path
        File scriptFile = resolveFilePath(filePath);
        if (!scriptFile.exists()) {
            throw new FileNotFoundException("Python script not found: " + filePath);
        }

        // Build spark-submit command
        List<String> command = buildSparkSubmitCommand(scriptFile, args, pool, sparkConfig);

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
        boolean completed = process.waitFor(10, TimeUnit.MINUTES);
        if (!completed) {
            process.destroy();
            throw new RuntimeException("Python job execution timed out after 10 minutes");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Python job failed with exit code: " + exitCode + "\n" + output);
        }

        log.info("Python job completed successfully");
        return output.toString();
    }

    /**
     * Execute inline Python code reactively as a Spark job.
     *
     * @param code Python code to execute
     * @param pool Scheduler pool name
     * @param sparkConfig Per-job Spark configuration overrides
     * @return Mono of execution result
     */
    public Mono<String> executePythonCodeReactive(String code, String pool, java.util.Map<String, String> sparkConfig) {
        return Mono.fromCallable(() -> executePythonCode(code, pool, sparkConfig))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Execute inline Python code as a Spark job.
     *
     * @param code Python code to execute
     * @param pool Scheduler pool name
     * @param sparkConfig Per-job Spark configuration overrides
     * @return Execution result
     */
    public String executePythonCode(String code, String pool, java.util.Map<String, String> sparkConfig) throws Exception {
        log.info("Executing inline Python code");

        // Create temporary Python file
        File tempFile = File.createTempFile("libra-python-", ".py");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(code);
        }

        log.info("Created temporary Python file: {}", tempFile.getAbsolutePath());

        // Execute the temporary file
        String result = executePythonFile(tempFile.getAbsolutePath(), null, pool, sparkConfig);

        // Clean up temp file
        try {
            Files.delete(tempFile.toPath());
        } catch (Exception e) {
            log.warn("Failed to delete temporary Python file", e);
        }

        return result;
    }

    /**
     * Resolve file path - supports absolute, relative, and classpath resources.
     */
    private File resolveFilePath(String filePath) throws IOException {
        // Handle classpath resources
        if (filePath.startsWith("classpath:")) {
            String resourcePath = filePath.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                throw new FileNotFoundException("Classpath resource not found: " + resourcePath);
            }

            // Extract to temp file
            File tempFile = File.createTempFile("libra-classpath-", ".py");
            tempFile.deleteOnExit();

            try (InputStream inputStream = resource.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            log.info("Extracted classpath resource to: {}", tempFile.getAbsolutePath());
            return tempFile;
        }

        // Handle absolute or relative paths
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            // Try relative to current working directory
            path = Paths.get(System.getProperty("user.dir"), filePath);
        }

        return path.toFile();
    }

    /**
     * Build spark-submit command with proper configurations.
     */
    private List<String> buildSparkSubmitCommand(File scriptFile, String[] args, String pool, java.util.Map<String, String> jobSparkConfig) {
        List<String> command = new ArrayList<>();

        // Find spark-submit executable
        String sparkSubmit = findSparkSubmit();
        command.add(sparkSubmit);

        // Add master
        String master = sparkConf.get("spark.master", "local[*]");
        command.add("--master");
        command.add(master);

        // Add app name
        String appName = sparkConf.get("spark.app.name", "whereq-libra") + "-python";
        command.add("--name");
        command.add(appName);

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

        // Add Python script
        command.add(scriptFile.getAbsolutePath());

        // Add script arguments
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
