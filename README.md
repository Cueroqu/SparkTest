# SparkTest

I don't know how to run it in other Spark cluster.

$FIREWORK_HOME/bin/spark-submit \
    --env els \
    --spark-version 1.5.1-SNAPSHOT \
    --master yarn-client \
    --class com.cueroqu.App \
    SparkTest-1.0-SNAPSHOT.jar \
    10
