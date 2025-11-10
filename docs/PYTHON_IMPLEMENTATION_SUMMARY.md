# WhereQ Libra - Python Execution Implementation Summary

## Overview

Successfully implemented **PySpark script execution support** for WhereQ Libra, enabling users to submit and execute Python scripts as Spark jobs via REST API.

## Implementation Date

2025-11-03

## User Request

> "I have all of my jobs wrote in a python script, I want to specify the python script file path in the spark-submit, Libra will create a spark session and run the python script as a spark job"

## Implementation Summary

### New Components Created

1. **PythonJobExecutor.java**
   - Location: `src/main/java/com/whereq/libra/service/PythonJobExecutor.java`
   - Purpose: Execute Python scripts using spark-submit
   - Features:
     - Execute Python files from filesystem
     - Execute inline Python code
     - Support for classpath resources
     - Command-line arguments support
     - Automatic spark-submit discovery (SPARK_HOME or PATH)
     - Timeout protection (10 minutes)
     - Output capture (stdout/stderr)

### Components Updated

2. **SparkJobRequest.java**
   - Added `filePath` field for Python script path
   - Added `args` field for command-line arguments
   - Updated `kind` to support: `sql`, `python`, `python-file`
   - Made `code` field optional (required only for sql/python)

3. **SparkSessionService.java**
   - Integrated PythonJobExecutor
   - Added `python` kind handler (inline code)
   - Added `python-file` kind handler (file execution)
   - Proper validation for required fields

### Sample Scripts Created

4. **scripts/hello_spark.py**
   - Basic PySpark demonstration
   - Creates DataFrame with sample data
   - Shows transformations and aggregations
   - Accepts command-line arguments

5. **scripts/word_count.py**
   - Classic word count example
   - Demonstrates text processing with PySpark
   - Uses DataFrame transformations
   - Shows groupBy and aggregations

### Test Scripts

6. **test-python-execution.sh**
   - Automated testing script
   - Tests python-file execution
   - Tests inline python execution
   - Demonstrates different resource pools

### Documentation

7. **PYTHON_EXECUTION.md**
   - Comprehensive user guide
   - Prerequisites and installation
   - Usage examples
   - Best practices
   - Troubleshooting guide
   - 200+ lines of documentation

## Features Implemented

### 1. Python File Execution

Execute Python scripts from filesystem:

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/hello_spark.py",
    "args": ["arg1", "arg2"],
    "pool": "default"
  }'
```

**Supported file paths:**
- Absolute: `/home/user/scripts/my_job.py`
- Relative: `scripts/my_job.py`
- Classpath: `classpath:scripts/my_job.py`

### 2. Inline Python Code Execution

Execute Python code directly without creating files:

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python",
    "code": "from pyspark.sql import SparkSession\nspark = SparkSession.builder.getOrCreate()\nprint(spark.version)\nspark.stop()"
  }'
```

### 3. Command-Line Arguments

Pass arguments to Python scripts:

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/process_data.py",
    "args": ["/data/input.csv", "/data/output"]
  }'
```

### 4. Classpath Resources

Load scripts from JAR resources:

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "classpath:scripts/bundled_job.py"
  }'
```

## Technical Implementation Details

### Execution Flow

```
API Request → Validate Input → Resolve File Path → Build spark-submit Command
     ↓
Execute spark-submit Process
     ↓
Capture Output (stdout/stderr)
     ↓
Wait for Completion (10 min timeout)
     ↓
Return Result or Error
```

### spark-submit Command Construction

The PythonJobExecutor automatically builds proper spark-submit commands:

```bash
spark-submit \
  --master local[*] \
  --name whereq-libra-python \
  --driver-memory 2g \
  --executor-memory 2g \
  --conf spark.scheduler.pool=default \
  --conf spark.scheduler.mode=FAIR \
  /path/to/script.py \
  arg1 arg2 arg3
```

All Spark configurations from `application.yml` are automatically included.

### File Path Resolution

1. **Classpath resources**: Extracted to temp file
2. **Relative paths**: Resolved from working directory
3. **Absolute paths**: Used directly

### Error Handling

- File not found errors
- spark-submit execution errors
- Timeout errors (10 minutes)
- Python script errors
- All captured and returned to client

## Integration with Existing Features

### FAIR Scheduler Integration

Python jobs work seamlessly with the FAIR scheduler:

```bash
# High priority Python job
{
  "kind": "python-file",
  "filePath": "scripts/critical_job.py",
  "pool": "high-priority"
}

# Low priority Python job
{
  "kind": "python-file",
  "filePath": "scripts/batch_job.py",
  "pool": "low-priority"
}
```

### Parallel Execution

Python jobs execute in parallel with SQL jobs:

- Python jobs use spark-submit subprocess
- SQL jobs use shared SparkSession
- Both managed by FAIR scheduler
- Resources allocated based on pool configuration

## Testing

### Build Status

✅ **BUILD SUCCESS** - All components compile successfully

### Test Scenarios

1. **Python File Execution**: ✅ Tested with `hello_spark.py`
2. **Word Count Example**: ✅ Tested with `word_count.py`
3. **Inline Python**: ✅ Tested with simple Spark code
4. **Command-Line Args**: ✅ Tested with multiple arguments
5. **Resource Pools**: ✅ Tested with different pool priorities

## Prerequisites

### System Requirements

- Python 3.x installed
- PySpark installed (`pip install pyspark`)
- spark-submit in PATH or SPARK_HOME set

### Installation

```bash
# Install PySpark
pip install pyspark

# Set SPARK_HOME (optional)
export SPARK_HOME=/path/to/spark
export PATH=$PATH:$SPARK_HOME/bin
```

## Response Format

### Success Response

```json
{
  "jobId": "uuid",
  "sessionId": "default",
  "state": "SUCCESS",
  "result": "==================================================\nHello from PySpark!\n==================================================\nSpark Version: 4.0.1\n...",
  "startedAt": "2025-11-03T12:00:00",
  "completedAt": "2025-11-03T12:00:30",
  "executionTimeMs": 30000
}
```

### Error Response

```json
{
  "jobId": "uuid",
  "sessionId": "default",
  "state": "FAILED",
  "error": "Python job failed with exit code: 1\nTraceback...",
  "startedAt": "2025-11-03T12:00:00",
  "completedAt": "2025-11-03T12:00:05",
  "executionTimeMs": 5000
}
```

## Files Modified/Created

### New Files

1. `src/main/java/com/whereq/libra/service/PythonJobExecutor.java`
2. `scripts/hello_spark.py`
3. `scripts/word_count.py`
4. `test-python-execution.sh`
5. `PYTHON_EXECUTION.md`
6. `PYTHON_IMPLEMENTATION_SUMMARY.md` (this file)

### Modified Files

1. `src/main/java/com/whereq/libra/dto/SparkJobRequest.java`
2. `src/main/java/com/whereq/libra/service/SparkSessionService.java`
3. `README.md`

## Configuration

No additional configuration required! The feature works out of the box if Python and PySpark are installed.

### Optional Configuration

```yaml
spark:
  config:
    spark.driver.memory: 4g  # Used in spark-submit
    spark.executor.memory: 4g
    spark.executor.cores: 2
```

## Limitations

1. **Timeout**: 10 minutes (hardcoded)
2. **No Streaming**: Not suitable for streaming jobs
3. **Process-Based**: Each job runs as separate process
4. **Dependencies**: Must be pre-installed on system
5. **No Interactive Mode**: Cannot use PySpark shell

## Best Practices

### 1. Script Structure

```python
from pyspark.sql import SparkSession

def main():
    spark = SparkSession.builder.getOrCreate()
    try:
        # Your logic here
        pass
    finally:
        spark.stop()

if __name__ == "__main__":
    main()
```

### 2. Error Handling

```python
import sys

try:
    # Your logic
    sys.exit(0)
except Exception as e:
    print(f"Error: {e}", file=sys.stderr)
    sys.exit(1)
```

### 3. Logging

```python
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

logger.info("Job started")
```

## Use Cases

### 1. ETL Pipelines

```bash
curl -X POST .../statements -d '{
  "kind": "python-file",
  "filePath": "scripts/etl/daily_load.py",
  "args": ["2025-11-03"],
  "pool": "batch"
}'
```

### 2. Data Analysis

```bash
curl -X POST .../statements -d '{
  "kind": "python-file",
  "filePath": "scripts/analysis/user_metrics.py",
  "pool": "interactive"
}'
```

### 3. Ad-hoc Queries

```bash
curl -X POST .../statements -d '{
  "kind": "python",
  "code": "...",
  "pool": "interactive"
}'
```

## Future Enhancements (Optional)

1. **Configurable Timeout**: Make timeout configurable
2. **Dependency Management**: Support for requirements.txt
3. **File Upload**: Upload scripts via API
4. **Progress Tracking**: Real-time progress updates
5. **Resource Profiling**: Capture resource usage metrics
6. **Python Environment**: Support for virtual environments

## Conclusion

The Python execution implementation is **production-ready** and provides:

✅ **Complete Feature**: File and inline execution
✅ **Well Documented**: Comprehensive user guide
✅ **Tested**: Sample scripts and test suite
✅ **Integrated**: Works with FAIR scheduler
✅ **Easy to Use**: Simple REST API

This feature significantly enhances WhereQ Libra's capabilities, making it a true alternative to Apache Livy for Python Spark job submission!
