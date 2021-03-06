package org.apress.prospark

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD.rddToOrderedRDDFunctions
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream.toPairDStreamFunctions
import kafka.serializer.StringDecoder
import org.apache.spark.streaming.kafka.KafkaUtils

object StationJourneyCountDirectApp {

  def main(args: Array[String]) {
    if (args.length != 7) {
      System.err.println(
        "Usage: StationJourneyCountApp <appname> <brokerUrl> <topic> <consumerGroupId> <zkQuorum> <checkpointDir> <outputPath>")
      System.exit(1)
    }

    val Seq(appName, brokerUrl, topic, consumerGroupId, zkQuorum, checkpointDir, outputPath) = args.toSeq

    val conf = new SparkConf()
      .setAppName(appName)
      .setJars(SparkContext.jarOfClass(this.getClass).toSeq)

    val ssc = new StreamingContext(conf, Seconds(10))
    ssc.checkpoint(checkpointDir)

    val topics = Set(topic)
    val params = Map[String, String](
      "zookeeper.connect" -> zkQuorum,
      "group.id" -> consumerGroupId,
      "bootstrap.servers" -> brokerUrl)
    KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, params, topics).map(_._2)
      .map(rec => rec.split(","))
      .map(rec => ((rec(3), rec(7)), 1))
      .reduceByKey(_ + _)
      .repartition(1)
      .map(rec => (rec._2, rec._1))
      .transform(rdd => rdd.sortByKey(ascending = false))
      .saveAsTextFiles(outputPath)

    ssc.start()
    ssc.awaitTermination()
  }

}