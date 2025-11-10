# WhereQ Libra Documentation Index

**Complete documentation for WhereQ Libra - Modern REST Interface for Apache Spark**

---

## 📚 Getting Started

### Quick Start
- **[Quick Start Guide](QUICK_START.md)** - Get up and running in 5 minutes

### Main Documentation
- **[README](../README.md)** - Project overview, features, and deployment models

---

## 🏗️ Architecture & Concepts

### Understanding Spark Architecture
- **[Spark Architecture Deep Dive](SPARK_ARCHITECTURE_DEEP_DIVE.md)** ⭐ **NEW**
  - Driver, Master, Workers, and Executors explained
  - Component interactions
  - Real-world examples (E-Commerce, ML, Streaming)
  - Common misconceptions debunked
  - 77KB, comprehensive guide

- **[Where Is The Driver?](WHERE_IS_THE_DRIVER.md)** ⭐ **NEW**
  - Understanding driver location in different deployments
  - Client mode vs Cluster mode
  - WhereQ Libra's driver architecture (SHARED vs ISOLATED)
  - Debugging tips: "Where is my driver?"
  - 34KB, focused guide

- **[Driver Performance Impact](DRIVER_PERFORMANCE_IMPACT.md)** ⭐ **NEW**
  - How important is the driver?
  - Does driver location affect job performance?
  - Network communication patterns explained
  - Real performance numbers: Client vs Cluster mode
  - Best practices and decision trees
  - 43KB, performance analysis

### Libra-Specific Architecture
- **[Implementation Summary](IMPLEMENTATION_SUMMARY.md)**
  - Core implementation details
  - Session management
  - Job execution flow

---

## 🚀 Deployment

### Deployment Guides
- **[Deployment Guide](DEPLOYMENT.md)**
  - Deployment strategies
  - Configuration management
  - Production best practices

### Kubernetes
- **[Kubernetes README](../k8s/README.md)**
  - Kubernetes manifests
  - Helm charts (if applicable)
  - K8s-specific configuration

---

## ⚙️ Feature Documentation

### Parallel Execution
- **[Parallel Execution Guide](PARALLEL_EXECUTION.md)**
  - SHARED mode (single SparkSession, FAIR scheduler)
  - ISOLATED mode (multiple SparkSessions)
  - Resource pools (default, high-priority, low-priority, interactive)
  - Use cases and examples

### Python Support
- **[Python Execution Guide](PYTHON_EXECUTION.md)**
  - Executing Python code snippets
  - Executing Python files
  - PySpark integration
  - Examples and best practices

- **[Python Implementation Summary](PYTHON_IMPLEMENTATION_SUMMARY.md)**
  - Implementation details
  - Architecture decisions
  - Technical reference

### JAR Execution
- **[JAR Execution Guide](JAR_EXECUTION.md)**
  - `jar` mode (spark-submit, dedicated resources)
  - `jar-class` mode (in-JVM, shared session)
  - SparkSession.getOrCreate() behavior
  - When to use each mode
  - Examples and best practices

---

## 🎛️ Resource Management

### Resource Configuration
- **[Resource Allocation Guide](RESOURCE_ALLOCATION.md)**
  - Global vs per-job configuration
  - Resource allocation by job type (jar, jar-class, python, python-file, sql)
  - Memory and core configuration
  - Dynamic allocation
  - Best practices and troubleshooting
  - 21KB, comprehensive guide

- **[Resource Config Enhancement](RESOURCE_CONFIG_ENHANCEMENT.md)**
  - Summary of resource configuration enhancements
  - Per-job `sparkConfig` field
  - Intelligent jar-class mode switching
  - Migration guide
  - Quick reference examples
  - 14KB, enhancement summary

---

## 📖 Documentation by Use Case

### I want to understand Spark fundamentals
1. Start with [Spark Architecture Deep Dive](SPARK_ARCHITECTURE_DEEP_DIVE.md)
2. Read [Where Is The Driver?](WHERE_IS_THE_DRIVER.md)
3. Understand [Driver Performance Impact](DRIVER_PERFORMANCE_IMPACT.md)
4. Review [Resource Allocation Guide](RESOURCE_ALLOCATION.md)

### I want to get started quickly
1. Follow [Quick Start Guide](QUICK_START.md)
2. Review [README](../README.md) for API examples
3. Check [Deployment Guide](DEPLOYMENT.md) for your environment

### I want to run Python jobs
1. Read [Python Execution Guide](PYTHON_EXECUTION.md)
2. Review [Resource Allocation Guide](RESOURCE_ALLOCATION.md) - Python section
3. Check [Resource Config Enhancement](RESOURCE_CONFIG_ENHANCEMENT.md) for per-job resources

### I want to run JAR jobs
1. Read [JAR Execution Guide](JAR_EXECUTION.md)
2. Understand `jar` vs `jar-class` modes
3. Review [Resource Allocation Guide](RESOURCE_ALLOCATION.md) - JAR section
4. Check [Resource Config Enhancement](RESOURCE_CONFIG_ENHANCEMENT.md) for intelligent switching

### I want to optimize resource usage
1. Read [Resource Allocation Guide](RESOURCE_ALLOCATION.md)
2. Review [Resource Config Enhancement](RESOURCE_CONFIG_ENHANCEMENT.md)
3. Check [Parallel Execution Guide](PARALLEL_EXECUTION.md) for SHARED vs ISOLATED modes
4. Study [Spark Architecture Deep Dive](SPARK_ARCHITECTURE_DEEP_DIVE.md) - Real-World Examples

### I want to deploy to production
1. Review [Deployment Guide](DEPLOYMENT.md)
2. Check [Kubernetes README](../k8s/README.md) if using K8s
3. Read [README](../README.md) - Deployment Models section
4. Review [Where Is The Driver?](WHERE_IS_THE_DRIVER.md) for understanding driver placement
5. Study [Driver Performance Impact](DRIVER_PERFORMANCE_IMPACT.md) for optimal driver placement

### I'm debugging an issue
1. Check [Where Is The Driver?](WHERE_IS_THE_DRIVER.md) - Debugging section
2. Review [Spark Architecture Deep Dive](SPARK_ARCHITECTURE_DEEP_DIVE.md) - Common Misconceptions
3. Check [Resource Allocation Guide](RESOURCE_ALLOCATION.md) - Troubleshooting section

---

## 📊 Documentation Statistics

| Document | Size | Focus | Audience |
|----------|------|-------|----------|
| **Spark Architecture Deep Dive** | 77KB | Spark fundamentals, architecture | All users |
| **Driver Performance Impact** | 43KB | Performance analysis, best practices | All users |
| **Where Is The Driver?** | 36KB | Driver location, deployment modes | All users |
| **Resource Allocation Guide** | 24KB | Resource configuration | Operations, developers |
| **Resource Config Enhancement** | 16KB | Enhancement summary, quick ref | Developers |
| **JAR Execution Guide** | 12KB | JAR job execution | Java developers |
| **Parallel Execution** | 12KB | Parallel execution modes | All users |
| **Python Execution Guide** | 12KB | Python job execution | Python developers |
| **Python Implementation** | 12KB | Technical implementation | Developers |
| **Deployment Guide** | 8KB | Deployment strategies | Operations |
| **Implementation Summary** | 8KB | Core implementation | Developers |
| **Quick Start** | 4KB | Getting started | New users |

---

## 🆕 Recently Added

- **[Driver Performance Impact](DRIVER_PERFORMANCE_IMPACT.md)** (2025-11-04) ⭐ **LATEST**
  - How important is the driver? (Answer: Critical but doesn't do heavy lifting)
  - Does driver location affect performance? (Answer: Minimal for most jobs!)
  - Real performance numbers comparing client vs cluster mode
  - Best practices and decision trees for WhereQ Libra deployment

- **[Spark Architecture Deep Dive](SPARK_ARCHITECTURE_DEEP_DIVE.md)** (2025-11-03)
  - Comprehensive guide to Spark components
  - Real-world examples with cost analysis
  - Common misconceptions explained

- **[Where Is The Driver?](WHERE_IS_THE_DRIVER.md)** (2025-11-03)
  - Answers the common question: "Where is the driver when I only see Master + Workers?"
  - Client vs Cluster mode explained with diagrams
  - Debugging tips and tools

- **[Resource Config Enhancement](RESOURCE_CONFIG_ENHANCEMENT.md)** (2025-11-03)
  - Per-job resource configuration for all modes
  - Intelligent jar-class auto-switching
  - Python and JAR modes now support custom resources

---

## 🔗 External Resources

- [Apache Spark Official Documentation](https://spark.apache.org/docs/latest/)
- [Apache Spark Architecture Overview](https://spark.apache.org/docs/latest/cluster-overview.html)
- [Spark Configuration Reference](https://spark.apache.org/docs/latest/configuration.html)
- [WhereQ Libra GitHub](https://github.com/whereq/spark-facade) *(if applicable)*

---

## 📝 Contributing to Documentation

When adding new documentation:

1. **Place in docs/ folder**: All documentation except root README.md
2. **Update this index**: Add entry with description
3. **Cross-reference**: Link to related docs
4. **Use relative links**:
   - Within docs/: `[Link](OTHER_DOC.md)`
   - To root README: `[Link](../README.md)`
5. **Add to "Recently Added"**: For new or significantly updated docs

---

## 🆘 Need Help?

- **Quick questions**: Check [Quick Start Guide](QUICK_START.md)
- **Architecture questions**: See [Spark Architecture Deep Dive](SPARK_ARCHITECTURE_DEEP_DIVE.md)
- **Deployment issues**: Review [Deployment Guide](DEPLOYMENT.md)
- **Resource tuning**: Read [Resource Allocation Guide](RESOURCE_ALLOCATION.md)
- **Debugging**: See [Where Is The Driver?](WHERE_IS_THE_DRIVER.md) - Debugging section

---

**Last Updated:** 2025-11-04
**Total Documentation:** 13 documents, ~220KB of comprehensive guides

---

**[Back to Main README](../README.md)**
