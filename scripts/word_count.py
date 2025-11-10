#!/usr/bin/env python3
"""
PySpark Word Count Example.
Classic word count demonstration.

Author: WhereQ Inc.
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import explode, split, col

def main():
    # Create SparkSession
    spark = SparkSession.builder \
        .appName("Word Count - WhereQ Libra") \
        .getOrCreate()

    print("=" * 50)
    print("PySpark Word Count Example")
    print("=" * 50)

    # Sample text data
    text_data = [
        ("Apache Spark is a unified analytics engine",),
        ("Spark provides high-level APIs in Java, Scala, Python and R",),
        ("Spark runs on Hadoop, Mesos, standalone, or in the cloud",),
        ("WhereQ Libra makes Spark even easier to use",),
        ("Spark Spark Spark is awesome!",)
    ]

    # Create DataFrame
    df = spark.createDataFrame(text_data, ["text"])

    print("\nOriginal text:")
    df.show(truncate=False)

    # Word count
    words_df = df.select(explode(split(col("text"), " ")).alias("word"))
    word_count_df = words_df.groupBy("word").count().orderBy(col("count").desc())

    print("\nWord Count Results:")
    word_count_df.show(20, truncate=False)

    total_words = words_df.count()
    unique_words = word_count_df.count()

    print(f"\nStatistics:")
    print(f"  Total words: {total_words}")
    print(f"  Unique words: {unique_words}")

    print("\n" + "=" * 50)
    print("Word count completed successfully!")
    print("=" * 50)

    spark.stop()

if __name__ == "__main__":
    main()
