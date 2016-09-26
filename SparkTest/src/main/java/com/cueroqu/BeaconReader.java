package com.cueroqu;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.catalyst.expressions.GenericRow;
import org.apache.spark.sql.hive.HiveContext;
import org.apache.spark.sql.types.LongType$;
import org.apache.spark.sql.types.StringType$;
import org.apache.spark.sql.types.StructType;
import scala.Tuple2;
import scala.Tuple3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zhun.qu on 9/21/16.
 */
public class BeaconReader {

    private static Pattern pattern;

    static {
        pattern = Pattern.compile("\\d+");
    }

    public static void main(String[] args) {
        SparkConf sparkConf = new SparkConf().setAppName("CueroReadsBeacon");
        JavaSparkContext javaSparkContext = new JavaSparkContext(sparkConf);
        String path = ""; // set to sequence file path
        JavaPairRDD<LongWritable, Text> dataSets = javaSparkContext.sequenceFile(path, LongWritable.class, Text.class);
        JavaPairRDD<String, Integer> rdd2 = dataSets.mapToPair(new PairFunction<Tuple2<LongWritable, Text>, String, Integer>() {
            @Override
            public Tuple2<String, Integer> call(Tuple2<LongWritable, Text> valuePair) {
                String[] values = valuePair._2.toString().split("\t");
                if (values.length >= 4) {
                    String beacon = values[3];
                    String date = values[0];
                    try {
                        if (beacon.startsWith("/v3/playback/end")) {
                            String[] paramsStr = beacon.split("\\?")[1].split("&");
                            for (String param : paramsStr) {
                                String[] pair = param.split("=");
                                if (pair[0].equals("contentid")) {
                                    if (pair.length < 2) {
                                        System.out.println(param);
                                    } else {
                                        Matcher m = pattern.matcher(pair[1]);
                                        if (m.find()) {
                                            return new Tuple2<>(date + "_" + m.group(0), 1);
                                        } else {
                                            System.out.println(param);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("CError: " + beacon);
                    }
                }
                return new Tuple2<>(null, 0);
            }
        });
        rdd2 = rdd2.filter(new Function<Tuple2<String, Integer>, Boolean>() {
            @Override
            public Boolean call(Tuple2<String, Integer> tuple) throws Exception {
                if (tuple._1 == null) return false;
//                String[] values = tuple._1.split("_");
//                if (values.length < 2) return false;
                if (tuple._2 == null) return false;
                return true;
            }
        });
        rdd2 = rdd2.reduceByKey(new Function2<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer integer, Integer integer2) throws Exception {
                return integer+integer2;
            }
        });
        JavaRDD<Row> rdd = rdd2.map(new Function<Tuple2<String, Integer>, Row>() {
            @Override
            public Row call(Tuple2<String, Integer> tuple) throws Exception {
                String[] values = tuple._1.split("_");
                Long id = Long.parseLong(values[1]);
                String date = values[0];
                Long count = Long.valueOf(tuple._2);
                return new GenericRow(new Object[]{id, date, count, date});
            }
        });
        StructType schema = new StructType()
                .add("contentid", LongType$.MODULE$)
                .add("view_date", StringType$.MODULE$)
                .add("view_count", LongType$.MODULE$)
                .add("vd", StringType$.MODULE$);
        rdd2.saveAsTextFile("output");
        System.setProperty("hive.metastore.uris", "thrift://metrics-hive-services-lb.prod.hulu.com:9083");
        final HiveContext hiveContext = new HiveContext(javaSparkContext.sc());
        hiveContext.setConf("hive.exec.dynamic.partition", "true");
        hiveContext.setConf("hive.exec.dynamic.partition.mode", "nonstrict");
        DataFrame dataFrame = hiveContext.createDataFrame(rdd, schema);
        dataFrame.write().partitionBy("vd").mode(SaveMode.Append).saveAsTable("cuero_view_count_1");

//        List<String> sqls = rdd2.map(new Function<Tuple2<String,Integer>, String>() {
//            @Override
//            public String call(Tuple2<String, Integer> tuple) throws Exception {
//                String[] values = tuple._1.split("_");
//                if (values.length < 2) {
//                    return "aaaaaaaaaaa: " + tuple._1;
//                }
////                String sql = tuple._1 + " " + tuple._2;
//                String sql = String.format("SELECT %s as contentid, '%s' as view_date, %d as view_count, '%s' as vd",
//                        values[1], values[0], tuple._2, values[0]);
//                return sql;
//            }
//        }).collect();
//        DataFrame df = hiveContext.sql(sqls.get(0));
//        for (String s : sqls) {
//            df.unionAll(hiveContext.sql(s));
//        }
//        df.write().partitionBy("vd").mode("append").saveAsTable("cuero_view_count_1");
//        sqls.saveAsTextFile("sqls");
//        sqls.foreach(new VoidFunction<String>() {
//            @Override
//            public void call(String sql) throws Exception {
////                System.out.println(sql);
//                if (sql == null) {
//                    System.out.println("sql is null");
//                    return;
//                }
//                if (hiveContext == null) {
//                    System.out.println("hiveContext is null");
//                    return;
//                }
//                DataFrame df = hiveContext.sql(sql);
//                df.write().partitionBy("vd").mode("append").saveAsTable("cuero_view_count_1");
//            }
//        });
//        hiveContext.sql("INSERT INTO cuero_test VALUES (5, 6)");
        javaSparkContext.close();
    }
}
