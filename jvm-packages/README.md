# XGBoost4J: Distributed XGBoost for Scala/Java
[![Build Status](https://travis-ci.org/dmlc/xgboost.svg?branch=master)](https://travis-ci.org/dmlc/xgboost)
[![Documentation Status](https://readthedocs.org/projects/xgboost/badge/?version=latest)](https://xgboost.readthedocs.org/en/latest/jvm/index.html)
[![GitHub license](http://dmlc.github.io/img/apache2.svg)](../LICENSE)

[Documentation](https://xgboost.readthedocs.org/en/latest/jvm/index.html) |
[Resources](../demo/README.md) |
[Release Notes](../NEWS.md)

XGBoost4J is the JVM package of xgboost. It brings all the optimizations
and power xgboost into JVM ecosystem.

- Train XGBoost models on scala and java with easy customizations.
- Run distributed xgboost natively on jvm frameworks such as Flink and Spark.

You can find more about XGBoost on [Documentation](https://xgboost.readthedocs.org/en/latest/jvm/index.html) and [Resource Page](../demo/README.md).

## Hello World
**NOTE on LIBSVM Format**: 
- Use *1-based* ascending indexes for the LIBSVM format in distributed training mode - 
  - Spark does the internal conversion, and does not accept formats that are 0-based
- Whereas, use *0-based* indexes format when predicting in normal mode - for instance, while using the saved model in the Python package

### XGBoost Scala
```scala
import ml.dmlc.xgboost4j.scala.DMatrix
import ml.dmlc.xgboost4j.scala.XGBoost

object XGBoostScalaExample {
  def main(args: Array[String]) {
    // read trainining data, available at xgboost/demo/data
    val trainData =
      new DMatrix("/path/to/agaricus.txt.train")
    // define parameters
    val paramMap = List(
      "eta" -> 0.1,
      "max_depth" -> 2,
      "objective" -> "binary:logistic").toMap
    // number of iterations
    val round = 2
    // train the model
    val model = XGBoost.train(trainData, paramMap, round)
    // run prediction
    val predTrain = model.predict(trainData)
    // save model to the file.
    model.saveModel("/local/path/to/model")
  }
}
```

### XGBoost Flink
#### stream
```scala
import ml.dmlc.xgboost4j.scala.flink.XGBoostModel
import ml.dmlc.xgboost4j.scala.flink.stream.XGBoost
import ml.dmlc.xgboost4j.scala.flink.utils.MLStreamUtils
import org.apache.flink.streaming.api.scala._

/**
  * Train and test an XGBoost model with flink stream API.
  */
object DistTrainWithFlink {
  def main(args: Array[String]) {
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // parallelism of the file read
    val readParallelism = 1
    // number of parallelism
    val numberOfParallelism = 1
    // dimension of the training data
    val dimension = 126
    // read training data
    val trainPath = "/path/to/data/agaricus.txt.train"
    val testPath = "/path/to/data/agaricus.txt.test"
    // define parameters
    val paramMap = List(
      "eta" -> 0.1,
      "max_depth" -> 2,
      "objective" -> "binary:logistic").toMap
    // number of iterations
    val round = 2
    // train the model then predict
    XGBoost.trainAndPredict(
          MLStreamUtils.readLibSVM(env, trainPath, dimension, readParallelism),
          MLStreamUtils.readLibSVM(env, testPath, dimension, readParallelism),
          paramMap, round, numberOfParallelism)
          .addSink(q => logger.info("result: " + q.deep.mkString("[", ",", "]")))
          .setParallelism(1)
    env.execute()
  }
}
```
#### batch
```scala
import ml.dmlc.xgboost4j.scala.DMatrix
import ml.dmlc.xgboost4j.scala.example.flink.utils.{Action, ParametersUtil}
import ml.dmlc.xgboost4j.scala.example.util.CustomEval
import ml.dmlc.xgboost4j.scala.flink.XGBoostModel
import ml.dmlc.xgboost4j.scala.flink.batch.XGBoost
import org.apache.flink.api.scala.{ExecutionEnvironment, _}
import org.apache.flink.ml.MLUtils

/**
  * Train and test an XGBoost model with flink batch API.
  */
object DistTrainWithFlink {
  def main(args: Array[String]) {
    val start = System.currentTimeMillis()
    val env: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    // number of parallelism
    val numberOfParallelism = 1
    // read training data
    val trainPath = "/path/to/data/agaricus.txt.train"
    val testPath = "/path/to/data/agaricus.txt.test"
    val validationMatrix = new DMatrix("/path/to/data/agaricus.txt.test")
    val eval = new CustomEval()

    val modelPath = "/data/lukacsg/sandbox/xgboosttestmodel/model"
    // define parameters for train
    val paramMap = List(
//      "eta" -> 0.1,
      "max_depth" -> 2,
      "objective" -> "binary:logistic").toMap
    // number of iterations
    val round = 2
    val prediction = XGBoost.trainAndPredict(MLUtils.readLibSVM(env, trainPath),
        MLUtils.readLibSVM(env, testPath),
        paramMap, round, numberOfParallelism)
    // test the prediction
      println(eval.eval(prediction, validationMatrix.jDMatrix) * 100 + "%")
  }
}
```
Additional flink examples can be found in [xgboost4j-example](https://github.com/streamline-eu/xgboost-jvm-packages/tree/master/jvm-packages/xgboost4j-example)

### XGBoost Spark

XGBoost4J-Spark supports training XGBoost model through RDD and Dataframe

RDD Version:

```scala
import org.apache.spark.SparkContext
import org.apache.spark.mllib.util.MLUtils
import ml.dmlc.xgboost4j.scala.spark.XGBoost

object SparkWithRDD {
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      println(
        "usage: program  num_of_rounds training_path model_path")
      sys.exit(1)
    }
    // if you do not want to use KryoSerializer in Spark, you can ignore the related configuration
    val sparkConf = new SparkConf().setMaster("local[*]").setAppName("XGBoost-spark-example")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    sparkConf.registerKryoClasses(Array(classOf[Booster]))
    val sc = new SparkContext(sparkConf)
    val inputTrainPath = args(1)
    val outputModelPath = args(2)
    // number of iterations
    val numRound = args(0).toInt
    val trainRDD = MLUtils.loadLibSVMFile(sc, inputTrainPath)
    // training parameters
    val paramMap = List(
      "eta" -> 0.1f,
      "max_depth" -> 2,
      "objective" -> "binary:logistic").toMap
    // use 5 distributed workers to train the model
    // useExternalMemory indicates whether 
    val model = XGBoost.train(trainRDD, paramMap, numRound, nWorkers = 5, useExternalMemory = true)
    // save model to HDFS path
    model.saveModelToHadoop(outputModelPath)
  }
}
```

Dataframe Version:

```scala
object SparkWithDataFrame {
  def main(args: Array[String]): Unit = {
    if (args.length != 5) {
      println(
        "usage: program num_of_rounds num_workers training_path test_path model_path")
      sys.exit(1)
    }
    // create SparkSession
    val sparkConf = new SparkConf().setAppName("XGBoost-spark-example")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    sparkConf.registerKryoClasses(Array(classOf[Booster]))
    val sparkSession = SparkSession.builder().appName("XGBoost-spark-example").config(sparkConf).
      getOrCreate()
    // create training and testing dataframes
    val inputTrainPath = args(2)
    val inputTestPath = args(3)
    val outputModelPath = args(4)
    // number of iterations
    val numRound = args(0).toInt
    import DataUtils._
    val trainRDDOfRows = MLUtils.loadLibSVMFile(sparkSession.sparkContext, inputTrainPath).
      map{ labeledPoint => Row(labeledPoint.features, labeledPoint.label)}
    val trainDF = sparkSession.createDataFrame(trainRDDOfRows, StructType(
      Array(StructField("features", ArrayType(FloatType)), StructField("label", IntegerType))))
    val testRDDOfRows = MLUtils.loadLibSVMFile(sparkSession.sparkContext, inputTestPath).
      zipWithIndex().map{ case (labeledPoint, id) =>
      Row(id, labeledPoint.features, labeledPoint.label)}
    val testDF = sparkSession.createDataFrame(testRDDOfRows, StructType(
      Array(StructField("id", LongType),
        StructField("features", ArrayType(FloatType)), StructField("label", IntegerType))))
    // training parameters
    val paramMap = List(
      "eta" -> 0.1f,
      "max_depth" -> 2,
      "objective" -> "binary:logistic").toMap
    val xgboostModel = XGBoost.trainWithDataset(
      trainDF, paramMap, numRound, nWorkers = args(1).toInt, useExternalMemory = true)
    // xgboost-spark appends the column containing prediction results
    xgboostModel.transform(testDF).show()
  }
}
```
