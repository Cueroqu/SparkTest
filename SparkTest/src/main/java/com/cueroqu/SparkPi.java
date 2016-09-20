package com.cueroqu;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhun.qu on 9/20/16.
 */
public class SparkPi {
    public static void main( String[] args )
    {
        SparkConf sparkConf = new SparkConf().setAppName("CueroJavaSparkPi");
        JavaSparkContext jsc = new JavaSparkContext(sparkConf);

        int slices = (args.length == 1) ? Integer.parseInt(args[0]) : 2;
        int n = 100000 * slices;
        List<Integer> l = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            l.add(0);
        }

        JavaRDD<Integer> dataSet = jsc.parallelize(l, slices);
        int count = dataSet.map(new Function<Integer, Integer>() {
            @Override
            public Integer call(Integer integer) throws Exception {
                double x = Math.random() * 2 - 1;
                double y = Math.random() * 2 - 1;
                return (x*x + y*y < 1) ? 1 : 0;
            }
        }).reduce(new Function2<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer integer, Integer integer2) throws Exception {
                return integer + integer2;
            }
        });

        List<Integer> res = new ArrayList<>();
        res.add(count);
        jsc.parallelize(res).saveAsTextFile("output.pi.txt");
        dataSet.saveAsTextFile("pi.txt");
        System.out.println("Hi Cuero, Pi is roughly " + 4.0 * count / n);

        jsc.stop();
    }
}
