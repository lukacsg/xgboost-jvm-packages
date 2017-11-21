# XGBoost4J-Flink: Distributed XGBoost for Flink
[![GitHub license](http://dmlc.github.io/img/apache2.svg)](../LICENSE)

XGBoost4J-Flink is the Flink implementation of Xgboost based on the [Xgboost4j](https://github.com/streamline-eu/xgboost-jvm-packages/tree/master/jvm-packages/xgboost4j). It brings all the optimizations and power xgboost into flink for both batch and streaming API.

- Train XGBoost models on Flink with easy customizations.
- Run distributed xgboost natively on Flink.

Let's see some example:
## Examples
Additional flink examples can be found in [xgboost4j-example](https://github.com/streamline-eu/xgboost-jvm-packages/tree/master/jvm-packages/xgboost4j-example)
### Stream
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
### Batch
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

## API
The API gives many easy-to-use functions so usage of the package is comfortable.
### Batch
 [XGBoost](https://github.com/streamline-eu/xgboost-jvm-packages/blob/master/jvm-packages/xgboost4j-flink/src/main/scala/ml/dmlc/xgboost4j/scala/flink/batch/XGBoost.scala)
 Functions:
  * `train(trainBatch: DataSet[LabeledVector],
            params: Map[String, Any],
            round: Int,
            numberOfParallelism: Int,
            trackerConf: TrackerConf = TrackerUtils.getDefaultTackerConf,
            obj: ObjectiveTrait = null,
            eval: EvalTrait = null
           ): XGBoostModel` 
Train a xgboost model with DataSet of LabeledVector.
    parameters:
    * **trainBatch**: the training data
    * **params** the parameters to XGBoost
    * **round** number of rounds to train
    * **numberOfParallelism** the parallelism of the training
    * **trackerConf** contains the necessary configuration to create the Tracker.
    * **obj** the user-defined objective function, null by default
    * **eval** the user-defined evaluation function, null by default
    * **return** the model XGBoostModel
 *  `predict(xGBoostModel: XGBoostModel,
              testBatch: DataSet[LabeledVector],
              numberOfParallelism: Int): Array[Array[Float]]`
Predict with the given DataSet of LabeledVector and XGBoostModel.
parameters:
    * **xGBoostModel** the trained model
    * **testBatch** the DataSet of LabeledVector for test
    * **numberOfParallelism** the number of parallelism of the prediction
    * **return** the prediction result
 * `trainAndSaveModelToFile(trainBatch: DataSet[LabeledVector],
                              params: Map[String, Any],
                              round: Int,
                              numberOfParallelism: Int,
                              filePath: String,
                              trackerConf: TrackerConf = TrackerUtils.getDefaultTackerConf,
                              saveAsHadoopFile: Boolean = true,
                              obj: ObjectiveTrait = null,
                              eval: EvalTrait = null
                             ): Unit`
Train and save a xgboost model to the given file path with DataSet of LabeledVector.
parameters:
    * **trainBatch** the training data
    * **params** the parameters to XGBoost
    * **round** number of rounds to train
    * **numberOfParallelism** the parallelism of the training
    * **filePath** the path of the model to save
    * **trackerConf** contains the necessary configuration to create the Tracker
    * **saveAsHadoopFile** the file path uses hadoop filesystem or not. Default is hadoop file.
    * **obj** the user-defined objective function, null by default
    * **eval** the user-defined evaluation function, null by default
* `trainAndPredict(trainBatch: DataSet[LabeledVector],
                      testBatch: DataSet[LabeledVector],
                      params: Map[String, Any],
                      round: Int,
                      numberOfParallelism: Int,
                      trackerConf: TrackerConf = TrackerUtils.getDefaultTackerConf,
                      obj: ObjectiveTrait = null,
                      eval: EvalTrait = null
                     ): Array[Array[Float]]`
Train with DataSet of LabeledVector then predict with the trained xgboost model.
This method offers a good and easy way to test / compare
the result of the training and prediction.
parameters:
    * **trainBatch** the training data
    * **testBatch** the test data
    * **params** the parameters to XGBoost
    * **round** number of rounds to train
    * **numberOfParallelism** the parallelism of the training
    * **trackerConf** contains the necessary configuration to create the Tracker
    * **obj** the user-defined objective function, null by default
    * **eval** the user-defined evaluation function, null by default
    * **return** the prediction result
        
### Stream
[XGBoost](https://github.com/streamline-eu/xgboost-jvm-packages/blob/master/jvm-packages/xgboost4j-flink/src/main/scala/ml/dmlc/xgboost4j/scala/flink/stream/XGBoost.scala)
Functions:
  * `train(trainStream: DataStream[LabeledVector],
            params: Map[String, Any],
            round: Int,
            numberOfParallelism: Int,
            trackerConf: TrackerConf = TrackerUtils.getDefaultTackerConf,
            obj: ObjectiveTrait = null,
            eval: EvalTrait = null
           ): DataStream[XGBoostModel]` 
Train a xgboost model with DataStream of LabeledVector.
    parameters:
    * **trainStream**: the training data
    * **params** the parameters to XGBoost
    * **round** number of rounds to train
    * **numberOfParallelism** the parallelism of the training
    * **trackerConf** contains the necessary configuration to create the Tracker.
    * **obj** the user-defined objective function, null by default
    * **eval** the user-defined evaluation function, null by default
    * **return** the model XGBoostModel in a DataStream
 *  `predict(xGBoostModel: XGBoostModel,
              testStream: DataStream[LabeledVector],
              numberOfParallelism: Int): DataStream[Array[Array[Float]]]`
Predict with the given DataStream of LabeledVector and XGBoostModel.
parameters:
    * **xGBoostModel** the trained model
    * **testStream** the DataStream of LabeledVector for test
    * **numberOfParallelism** the number of parallelism of the prediction
    * **return** the prediction result DataStream of Array[Array[Float]]
 * `trainAndSaveModelToFile(trainStream: DataStream[LabeledVector],
                              params: Map[String, Any],
                              round: Int,
                              numberOfParallelism: Int,
                              filePath: String,
                              trackerConf: TrackerConf = TrackerUtils.getDefaultTackerConf,
                              saveAsHadoopFile: Boolean = true,
                              obj: ObjectiveTrait = null,
                              eval: EvalTrait = null
                             ): Unit`
Train and save a xgboost model to the given file path with DataStream of LabeledVector.
parameters:
    * **trainStream** the training data
    * **params** the parameters to XGBoost
    * **round** number of rounds to train
    * **numberOfParallelism** the parallelism of the training
    * **filePath** the path of the model to save
    * **trackerConf** contains the necessary configuration to create the Tracker
    * **saveAsHadoopFile** the file path uses hadoop filesystem or not. Default is hadoop file.
    * **obj** the user-defined objective function, null by default
    * **eval** the user-defined evaluation function, null by default
* `trainAndPredict(trainStream: DataStream[LabeledVector],
                      testStream: DataStream[LabeledVector],
                      params: Map[String, Any],
                      round: Int,
                      numberOfParallelism: Int,
                      trackerConf: TrackerConf = TrackerUtils.getDefaultTackerConf,
                      obj: ObjectiveTrait = null,
                      eval: EvalTrait = null
                     ): DataStream[Array[Array[Float]]]`
Train with DataStream of LabeledVector then predict with the trained xgboost model.
This method offers a good and easy way to test / compare
the result of the training and prediction.
parameters:
    * **trainStream** the training data
    * **testStream** the test data
    * **params** the parameters to XGBoost
    * **round** number of rounds to train
    * **numberOfParallelism** the parallelism of the training
    * **trackerConf** contains the necessary configuration to create the Tracker
    * **obj** the user-defined objective function, null by default
    * **eval** the user-defined evaluation function, null by default
    * **return** the prediction result DataStream of Array[Array[Float]]



