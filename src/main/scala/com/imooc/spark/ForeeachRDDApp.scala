package com.imooc.spark

import java.sql.DriverManager

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  * 使用Spark Streaming完成词频统计,并将结果写入到MySQL数据库中
  */
object ForeeachRDDApp {

  def main(args: Array[String]): Unit = {

    val sparkConf = new SparkConf().setMaster("local[2]").setAppName("ForeeachRDDApp")
    val ssc = new StreamingContext(sparkConf, Seconds(5))

    //如果使用了stateful的算子，必须要设置checkpoint
    //在生产环境中，建议大家把checkpoint设置到HDFS的某个文件夹中
    ssc.checkpoint(".")

    val lines = ssc.socketTextStream("192.168.246.131", 6789)
    val result = lines.flatMap(_.split(" ")).map((_,1)).reduceByKey(_+_)
    val state = result.updateStateByKey[Int](updateFunction _)

    //state.print()   //此处仅仅是将结果输出到控制台

    //TODO... 将结果写入到MySQL

//     //Task not serializable
//    result.foreachRDD(rdd =>{
//      val connection = createConnection()
//      rdd.foreach{ record =>
//        val sql = "insert into wordcount(word, wordcount) values('" + record._1 + "'," + record._2 + ")"
//        connection.createStatement().execute(sql)
//      }
//    })

    /**
      * 将DStream数据转化为RDD格式写进数据库，先获得partition
      */
        result.foreachRDD(rdd =>{

          rdd.foreachPartition(partitionOfRecords => {
              val connection = createConnection()
              partitionOfRecords.foreach(record =>{
                val sql = "insert into wordcount(word, wordcount) values('" + record._1 + "'," + record._2 + ")"
                connection.createStatement().execute(sql)
              })
              connection.close()
          })
        })


    ssc.start()
    ssc.awaitTermination()
  }

  /**
    * 把当前的数据去更新已有的或者是老的数据
    * @param currentValues  当前的
    * @param preValues   老的
    * @return
    */
  def updateFunction(currentValues: Seq[Int], preValues: Option[Int]): Option[Int] ={
    val current = currentValues.sum
    val pre = preValues.getOrElse(0)

    Some(current + pre)
  }

  /**
    * 获取MySQL的连接
    * @return
    */
  def createConnection() = {
    Class.forName("com.mysql.jdbc.Driver")
    DriverManager.getConnection("jdbc:mysql://192.168.246.131:3306/imooc_spark","root","950513")
  }
}
