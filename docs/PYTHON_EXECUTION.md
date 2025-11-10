# WhereQ Libra - Python Script Execution Guide

## Overview

WhereQ Libra supports executing Python scripts as Spark jobs in three ways:
1. **Python File Execution** - Submit a Python script file path
2. **Inline Python Code** - Execute Python code directly
3. **Classpath Resources** - Load Python scripts from JAR resources

This feature uses `spark-submit` internally to execute Python scripts with proper Spark configurations.

## Prerequisites

### Required

- **Python 3.x** installed on the system
- **PySpark** installed (`pip install pyspark`)
- **SPARK_HOME** environment variable set (optional but recommended)
- **spark-submit** executable in PATH

### Installation

```bash
# Install PySpark
pip install pyspark

# Set SPARK_HOME (optional)
export SPARK_HOME=/path/to/spark
export PATH=$PATH:$SPARK_HOME/bin
```

## Usage

### 1. Execute Python File

Submit a Python script file by providing its path.

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "/path/to/script.py",
    "args": ["arg1", "arg2"],
    "pool": "default"
  }'
```

**Request Fields:**
- `kind`: Must be `"python-file"`
- `filePath`: Path to Python script (absolute, relative, or classpath)
- `args`: Array of command-line arguments (optional)
- `pool`: Scheduler pool (optional, default: "default")

**File Path Options:**

1. **Absolute Path:**
   ```json
   "filePath": "/home/user/scripts/my_job.py"
   ```

2. **Relative Path:** (relative to application working directory)
   ```json
   "filePath": "scripts/my_job.py"
   ```

3. **Classpath Resource:**
   ```json
   "filePath": "classpath:scripts/my_job.py"
   ```

### 2. Execute Inline Python Code

Execute Python code directly without creating a file.

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python",
    "code": "from pyspark.sql import SparkSession\nspark = SparkSession.builder.getOrCreate()\nprint(spark.version)\nspark.stop()",
    "pool": "interactive"
  }'
```

**Request Fields:**
- `kind`: Must be `"python"`
- `code`: Python code to execute
- `pool`: Scheduler pool (optional)

**Note:** Inline code is written to a temporary file and executed via spark-submit.

### 3. Example Python Scripts

#### Simple Hello World

```python
# scripts/hello_spark.py
from pyspark.sql import SparkSession

def main():
    spark = SparkSession.builder \
        .appName("Hello Spark") \
        .getOrCreate()

    print(f"Spark Version: {spark.version}")

    # Create sample data
    data = [("Alice", 25), ("Bob", 30)]
    df = spark.createDataFrame(data, ["name", "age"])
    df.show()

    spark.stop()

if __name__ == "__main__":
    main()
```

#### Word Count Example

```python
# scripts/word_count.py
from pyspark.sql import SparkSession
from pyspark.sql.functions import explode, split, col

def main():
    spark = SparkSession.builder.getOrCreate()

    text_data = [
        ("Apache Spark is awesome",),
        ("Spark runs everywhere",)
    ]

    df = spark.createDataFrame(text_data, ["text"])
    words_df = df.select(explode(split(col("text"), " ")).alias("word"))
    word_count = words_df.groupBy("word").count().orderBy(col("count").desc())

    word_count.show()
    spark.stop()

if __name__ == "__main__":
    main()
```

#### With Command-Line Arguments

```python
# scripts/process_data.py
import sys
from pyspark.sql import SparkSession

def main():
    if len(sys.argv) < 2:
        print("Usage: process_data.py <input_path>")
        sys.exit(1)

    input_path = sys.argv[1]
    spark = SparkSession.builder.getOrCreate()

    print(f"Processing data from: {input_path}")
    # Your processing logic here

    spark.stop()

if __name__ == "__main__":
    main()
```

**Submit with arguments:**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/process_data.py",
    "args": ["/data/input.csv", "/data/output"]
  }'
```

## Configuration

### Spark Submit Parameters

The following Spark configurations are automatically passed to spark-submit:

- `--master`: From `spark.master` config
- `--name`: From `spark.app.name` + "-python"
- `--driver-memory`: From `spark.driver.memory`
- `--executor-memory`: From `spark.executor.memory`
- `--conf spark.scheduler.pool`: From request pool parameter
- `--conf spark.scheduler.mode`: FAIR scheduler mode

### Custom Spark Configuration

To add custom Spark configurations, modify `application.yml`:

```yaml
spark:
  config:
    spark.driver.memory: 4g
    spark.executor.memory: 4g
    spark.executor.cores: 2
    spark.sql.shuffle.partitions: 100
```

These will be automatically passed to spark-submit.

## Execution Flow

### Python File Execution Flow

```
1. API Request
   ↓
2. Validate filePath
   ↓
3. Resolve file path (absolute/relative/classpath)
   ↓
4. Build spark-submit command
   ↓
5. Execute spark-submit as subprocess
   ↓
6. Capture output (stdout/stderr)
   ↓
7. Wait for completion (timeout: 10 minutes)
   ↓
8. Return result or error
```

### Inline Python Execution Flow

```
1. API Request with code
   ↓
2. Create temporary .py file
   ↓
3. Write code to temp file
   ↓
4. Execute via spark-submit
   ↓
5. Capture output
   ↓
6. Delete temp file
   ↓
7. Return result
```

## Response Format

**Success Response:**
```json
{
  "jobId": "uuid",
  "sessionId": "default",
  "state": "SUCCESS",
  "result": "... Python script output ...",
  "startedAt": "2025-11-03T12:00:00",
  "completedAt": "2025-11-03T12:00:30",
  "executionTimeMs": 30000
}
```

**Error Response:**
```json
{
  "jobId": "uuid",
  "sessionId": "default",
  "state": "FAILED",
  "error": "Python job failed with exit code: 1\n...",
  "startedAt": "2025-11-03T12:00:00",
  "completedAt": "2025-11-03T12:00:05",
  "executionTimeMs": 5000
}
```

## Best Practices

### 1. Script Organization

```
project-root/
├── scripts/
│   ├── etl/
│   │   ├── extract.py
│   │   ├── transform.py
│   │   └── load.py
│   ├── analysis/
│   │   ├── daily_report.py
│   │   └── metrics.py
│   └── utils/
│       └── helpers.py
```

### 2. Use SparkSession Properly

```python
# Good: Create and stop session
spark = SparkSession.builder.getOrCreate()
try:
    # Your logic
    pass
finally:
    spark.stop()

# Better: Use context manager
from contextlib import contextmanager

@contextmanager
def spark_session(app_name):
    spark = SparkSession.builder.appName(app_name).getOrCreate()
    try:
        yield spark
    finally:
        spark.stop()

with spark_session("MyApp") as spark:
    # Your logic
    pass
```

### 3. Error Handling

```python
import sys
from pyspark.sql import SparkSession

def main():
    try:
        spark = SparkSession.builder.getOrCreate()

        # Your logic here

        spark.stop()
        return 0
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
```

### 4. Logging

```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

logger.info("Starting Spark job")
logger.warning("Warning message")
logger.error("Error message")
```

## Limitations

1. **Timeout**: Python jobs timeout after 10 minutes
2. **No Interactive Mode**: Cannot use interactive PySpark shell
3. **Output Capture**: Only stdout/stderr are captured
4. **Process-Based**: Each job runs as separate spark-submit process
5. **Dependencies**: All required Python packages must be installed on the system

## Troubleshooting

### Issue: spark-submit not found

**Symptom:**
```
Error: Cannot run program "spark-submit"
```

**Solution:**
1. Install Spark and set SPARK_HOME:
   ```bash
   export SPARK_HOME=/path/to/spark
   export PATH=$PATH:$SPARK_HOME/bin
   ```

2. Or install PySpark via pip (includes spark-submit):
   ```bash
   pip install pyspark
   ```

### Issue: Python script not found

**Symptom:**
```
Python script not found: scripts/my_job.py
```

**Solution:**
1. Use absolute path:
   ```json
   "filePath": "/full/path/to/scripts/my_job.py"
   ```

2. Or ensure script is in working directory:
   ```bash
   cd /path/to/project
   java -jar libra.jar
   ```

### Issue: Import errors in Python script

**Symptom:**
```
ModuleNotFoundError: No module named 'my_module'
```

**Solution:**
1. Install required packages:
   ```bash
   pip install my_module
   ```

2. Or add to PYTHONPATH:
   ```bash
   export PYTHONPATH=/path/to/modules:$PYTHONPATH
   ```

### Issue: Job times out

**Symptom:**
```
Python job execution timed out after 10 minutes
```

**Solution:**
1. Optimize your Spark job for better performance
2. The timeout is currently hardcoded to 10 minutes
3. For long-running jobs, consider batch processing or async execution

## Examples

### Example 1: Daily ETL Job

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/etl/daily_process.py",
    "args": ["2025-11-03"],
    "pool": "batch",
    "description": "Daily ETL for 2025-11-03"
  }'
```

### Example 2: Ad-hoc Analysis

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/analysis/user_metrics.py",
    "pool": "interactive",
    "description": "User metrics analysis"
  }'
```

### Example 3: Quick Test

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python",
    "code": "from pyspark.sql import SparkSession; spark = SparkSession.builder.getOrCreate(); print(f\"Cores: {spark.sparkContext.defaultParallelism}\"); spark.stop()",
    "pool": "interactive"
  }'
```

## Summary

WhereQ Libra's Python execution feature provides:

✅ **Flexible Execution**: File paths, inline code, or classpath resources
✅ **Spark Integration**: Automatic spark-submit with proper configurations
✅ **Parallel Support**: Works with FAIR scheduler and resource pools
✅ **Easy to Use**: Simple REST API for job submission
✅ **Production Ready**: Error handling, timeouts, and output capture

For more examples, see the `scripts/` directory and `test-python-execution.sh`.
