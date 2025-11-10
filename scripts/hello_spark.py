#!/usr/bin/env python3
"""
Simple PySpark script for testing.
Demonstrates basic Spark operations.

Author: WhereQ Inc.
"""

from pyspark.sql import SparkSession
import sys

def main():
    # Create SparkSession
    spark = SparkSession.builder \
        .appName("Hello Spark - WhereQ Libra") \
        .getOrCreate()

    print("=" * 50)
    print("Hello from PySpark!")
    print("=" * 50)
    print(f"Spark Version: {spark.version}")
    print(f"Application ID: {spark.sparkContext.applicationId}")
    print(f"Master: {spark.sparkContext.master}")
    print("=" * 50)

    # Create a simple DataFrame
    data = [
        ("Alice", 25, "Engineer"),
        ("Bob", 30, "Manager"),
        ("Charlie", 35, "Director"),
        ("Diana", 28, "Engineer"),
        ("Eve", 32, "Manager")
    ]

    df = spark.createDataFrame(data, ["name", "age", "role"])

    print("\nOriginal DataFrame:")
    df.show()

    # Perform some transformations
    print("\nEngineers only:")
    df.filter(df.role == "Engineer").show()

    print("\nAverage age by role:")
    df.groupBy("role").avg("age").show()

    # Print arguments if provided
    if len(sys.argv) > 1:
        print("\nScript arguments:")
        for i, arg in enumerate(sys.argv[1:], 1):
            print(f"  Arg {i}: {arg}")

    print("\n" + "=" * 50)
    print("Script completed successfully!")
    print("=" * 50)

    spark.stop()

if __name__ == "__main__":
    main()
