# WhereQ Libra - JAR Execution Guide

## Overview

WhereQ Libra supports executing Java-based Spark applications packaged as JARs. This allows you to submit your existing Spark applications to Libra **without any code changes**.

## Your Question Answered

> "Can you integrate this kind of job to Libra as well? Since inside the application it will getOrCreate a spark session, but in Libra you will create the SparkSession before running the job... does it mean the application can be integrated without big code refactoring?"

**YES! No code changes needed!** ✅

### How SparkSession.getOrCreate() Works

When your application calls `SparkSession.builder().getOrCreate()`:

1. Spark checks if there's already an active Spark Session in the current JVM
2. If found, it **returns the existing session**
3. If not found, it creates a new one

### Two Execution Modes

Libra provides **two modes** for JAR execution:

| Mode | Description | SparkSession Behavior | Use Case |
|------|-------------|----------------------|----------|
| **jar-class** | In-JVM execution | Reuses Libra's existing session | ✅ Recommended - Shares resources |
| **jar** | spark-submit (separate process) | Creates new session | Complete isolation |

## Mode 1: jar-class (Recommended)

### How It Works

```
1. Libra loads your JAR into its JVM
   ↓
2. Invokes your main() method using reflection
   ↓
3. Your code calls SparkSession.builder().getOrCreate()
   ↓
4. Spark finds Libra's existing SparkSession and returns it
   ↓
5. Your job runs using the shared session
```

### Benefits

- ✅ **No code changes** - Your existing app works as-is
- ✅ **Shares resources** - Uses Libra's SparkSession efficiently
- ✅ **Faster startup** - No need to create new SparkContext
- ✅ **Lower overhead** - No separate JVM process

### Important Notes

**DO NOT call `spark.stop()`** when using `jar-class` mode!

```java
// ❌ BAD - Don't do this with jar-class
spark.stop();  // This will stop the shared session!

// ✅ GOOD - Just let main() method complete
return;  // Session stays active for other jobs
```

### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar-class",
    "filePath": "sample-spark-app/target/simple-spark-app-1.0.0.jar",
    "mainClass": "com.example.spark.SimpleSparkApp",
    "args": ["arg1", "arg2"],
    "pool": "default"
  }'
```

## Mode 2: jar (spark-submit)

### How It Works

```
1. Libra calls spark-submit with your JAR
   ↓
2. spark-submit starts new JVM process
   ↓
3. Your main() method executes in separate JVM
   ↓
4. Your code calls SparkSession.builder().getOrCreate()
   ↓
5. Spark creates NEW SparkSession (separate from Libra's)
   ↓
6. Job runs in complete isolation
```

### Benefits

- ✅ **Complete isolation** - Separate JVM and SparkContext
- ✅ **No resource sharing conflicts** - Independent execution
- ✅ **Can call spark.stop()** - Doesn't affect Libra's session

### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar",
    "filePath": "sample-spark-app/target/simple-spark-app-1.0.0.jar",
    "mainClass": "com.example.spark.SimpleSparkApp",
    "args": ["arg1", "arg2"],
    "pool": "default"
  }'
```

## Understanding SparkContext per JVM

### Your Question:

> "I know there is supposed to be only one SparkSession per SparkContext, is it correct?"

**More accurately**: One **SparkContext per JVM** (by default)

```
┌─────────────────────────────────────┐
│         Libra JVM                   │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  SparkContext                 │  │
│  │   ↓                           │  │
│  │  SparkSession (shared)        │  │
│  │   ↑                           │  │
│  │   └── getOrCreate() returns   │  │
│  │       this same session       │  │
│  └───────────────────────────────┘  │
│                                     │
│  Your JAR's main() method runs here │
│  and gets the existing session      │
└─────────────────────────────────────┘
```

## Sample Application Code

Here's a complete example that works with **both modes** without any code changes:

```java
package com.example.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public class SimpleSparkApp {

    public static void main(String[] args) {
        // This works with BOTH jar-class and jar modes!
        // - jar-class: Returns Libra's existing session
        // - jar: Creates new session in separate process
        SparkSession spark = SparkSession.builder()
                .appName("SimpleSparkApp")
                .getOrCreate();

        System.out.println("Spark Version: " + spark.version());
        System.out.println("Application ID: " + spark.sparkContext().applicationId());

        // Your business logic here
        Dataset<Row> df = spark.read()
                .format("parquet")
                .load("/path/to/data");

        df.show();
        df.write()
                .format("parquet")
                .save("/path/to/output");

        // IMPORTANT:
        // - jar-class: DO NOT call spark.stop()
        // - jar: You CAN call spark.stop() if desired

        // To make it work for both modes, just don't call stop()
        // The session will be properly cleaned up
    }
}
```

## Building Your Application

### Maven pom.xml

```xml
<dependencies>
    <!-- Spark dependencies with 'provided' scope -->
    <dependency>
        <groupId>org.apache.spark</groupId>
        <artifactId>spark-sql_2.13</artifactId>
        <version>4.0.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Shade plugin to create fat JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.example.spark.SimpleSparkApp</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Build Command

```bash
cd sample-spark-app
mvn clean package
```

## Comparison Table

| Feature | jar-class (In-JVM) | jar (spark-submit) |
|---------|-------------------|-------------------|
| **Execution** | Same JVM as Libra | Separate JVM process |
| **SparkSession** | Shares Libra's session | Creates new session |
| **Performance** | Faster (no JVM startup) | Slower (JVM overhead) |
| **Resource Usage** | Lower (shared resources) | Higher (dedicated resources) |
| **Isolation** | Shared with other jobs | Complete isolation |
| **spark.stop()** | ❌ Do NOT call | ✅ Can call |
| **Code Changes** | ✅ None needed | ✅ None needed |
| **Best For** | Most use cases | Jobs needing isolation |

## Request/Response Examples

### Request Format

```json
{
  "kind": "jar-class",  // or "jar"
  "filePath": "path/to/your-app.jar",
  "mainClass": "com.example.YourMainClass",
  "args": ["arg1", "arg2", "arg3"],
  "pool": "default",
  "description": "My Spark job"
}
```

### Success Response

```json
{
  "jobId": "uuid",
  "sessionId": "default",
  "state": "SUCCESS",
  "result": "=== STDOUT ===\nSpark Version: 4.0.1\nApplication ID: local-...\n...",
  "startedAt": "2025-11-03T12:00:00",
  "completedAt": "2025-11-03T12:00:30",
  "executionTimeMs": 30000
}
```

## Common Patterns

### Pattern 1: ETL Job (jar-class recommended)

```bash
curl -X POST .../statements -d '{
  "kind": "jar-class",
  "filePath": "jars/etl-pipeline.jar",
  "mainClass": "com.company.etl.DailyETL",
  "args": ["2025-11-03", "/data/input", "/data/output"],
  "pool": "batch"
}'
```

### Pattern 2: ML Training (jar for isolation)

```bash
curl -X POST .../statements -d '{
  "kind": "jar",
  "filePath": "jars/ml-training.jar",
  "mainClass": "com.company.ml.TrainModel",
  "args": ["/data/features", "/models/output"],
  "pool": "high-priority"
}'
```

### Pattern 3: Report Generation

```bash
curl -X POST .../statements -d '{
  "kind": "jar-class",
  "filePath": "jars/reports.jar",
  "mainClass": "com.company.reports.DailyReport",
  "args": ["2025-11-03"],
  "pool": "interactive"
}'
```

## Migration from spark-submit

If you're currently using spark-submit:

```bash
# Old way
spark-submit \
  --class com.example.MyApp \
  --master yarn \
  my-app.jar arg1 arg2
```

Becomes:

```bash
# New way with Libra
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar-class",
    "filePath": "my-app.jar",
    "mainClass": "com.example.MyApp",
    "args": ["arg1", "arg2"]
  }'
```

## Best Practices

### 1. Use jar-class by Default

Unless you need complete isolation, use `jar-class` for better performance.

### 2. Don't Call spark.stop()

Make your code work for both modes by not calling `spark.stop()`:

```java
// ❌ BAD
spark.stop();

// ✅ GOOD
// Just return from main(), let Libra manage the session
```

### 3. Handle Arguments Properly

```java
public static void main(String[] args) {
    if (args.length < 2) {
        System.err.println("Usage: MyApp <input> <output>");
        System.exit(1);
    }

    String input = args[0];
    String output = args[1];
    // ... rest of logic
}
```

### 4. Use Proper Logging

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyApp {
    private static final Logger log = LoggerFactory.getLogger(MyApp.class);

    public static void main(String[] args) {
        log.info("Starting job with args: {}", Arrays.toString(args));
        // ... logic
        log.info("Job completed successfully");
    }
}
```

## Troubleshooting

### Issue: NoClassDefFoundError

**Symptom:**
```
java.lang.NoClassDefFoundError: org/apache/spark/sql/SparkSession
```

**Solution:**
Make sure Spark dependencies are marked as `provided` in your pom.xml:

```xml
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-sql_2.13</artifactId>
    <scope>provided</scope>
</dependency>
```

### Issue: JAR not found

**Symptom:**
```
JAR file not found: my-app.jar
```

**Solution:**
Use absolute path or ensure JAR is in working directory:

```json
"filePath": "/full/path/to/my-app.jar"
```

### Issue: Main class not found

**Symptom:**
```
ClassNotFoundException: com.example.MyApp
```

**Solution:**
Verify the fully qualified class name:

```bash
jar tf my-app.jar | grep MyApp
# Should show: com/example/MyApp.class
```

## Summary

✅ **No code changes needed** - Your existing Spark apps work as-is
✅ **getOrCreate() works correctly** - Finds existing session in jar-class mode
✅ **Two modes available** - Choose based on your isolation needs
✅ **One SparkContext per JVM** - jar-class shares, jar creates new
✅ **Simple integration** - Just provide JAR path and main class

Your question is answered: **YES, your application can be integrated without big code refactoring!** The `getOrCreate()` call will automatically find and reuse Libra's SparkSession when using `jar-class` mode.
