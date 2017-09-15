/*
 Copyright (c) 2014 by Contributors

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package ml.dmlc.xgboost4j.scala.flink

import ml.dmlc.xgboost4j.LabeledPoint
import ml.dmlc.xgboost4j.scala.{Booster, DMatrix}
import org.apache.commons.logging.{Log, LogFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala.{DataSet, _}
import org.apache.flink.ml.math.{DenseVector, SparseVector, Vector}
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

class XGBoostModel(booster: Booster) extends Serializable {
  val logger: Log = LogFactory.getLog(this.getClass)

  /**
    * Save the model as a Hadoop filesystem file or as local filesystem file.
    *
    * @param modelPath The model path as in Hadoop path.
    * @param asHadoopFile Is the file path Hadoop filesystem file or not.
    *                     Default th file path will be dealt as a Hadoop filesystem file.
    */
  def saveModel(modelPath: String, asHadoopFile: Boolean = true): Unit = {
    if (asHadoopFile) {
      booster.saveModel(FileSystem
        .get(new Configuration)
        .create(new Path(modelPath)))
    }
    else {
      booster.saveModel(modelPath)
    }
  }

  /**
    * Predict with the given [[DMatrix]].
    *
    * @param testSet the local test set represented as DMatrix
    * @return prediction result
    */
  def predict(testSet: DMatrix): Array[Array[Float]] = {
    booster.predict(testSet, outPutMargin = true)
  }

  /**
    * Batch predict.
    * Predict with the given [[DataSet]] of [[Vector]] .
    *
    * @param data the [[DataSet]] of [[Vector]] to be predicted
    * @return the prediction result
    */
  def predict(data: DataSet[Vector], numberOfParallelism: Int) : DataSet[Array[Float]] = {
    val input =
      if (data.getParallelism != numberOfParallelism) {
        logger.info(s"repartitioning training set to $numberOfParallelism partitions")
        data.rebalance.map(x => x).setParallelism(numberOfParallelism)
      } else {
        data
      }
    val predictMap: Iterator[Vector] => Traversable[Array[Float]] =
      (it: Iterator[Vector]) => {
        if (it.isEmpty) {
          Some(Array.empty[Float])
        } else {
          val mapper = (x: Vector) => {

            var index: Array[Int] = Array[Int]()
            var value: Array[Double] = Array[Double]()
            x match {
              case s: SparseVector =>
                index = s.indices
                value = s.data
              case d: DenseVector =>
                val (i, v) = d.toSeq.unzip
                index = i.toArray
                value = v.toArray
            }
            LabeledPoint(0.0f,
              index, value.map(z => z.toFloat))
          }
          val dataIter = for (x <- it) yield mapper(x)
          val dMatrix = new DMatrix(dataIter, null)
          this.booster.predict(dMatrix)
        }
      }
    input.mapPartition(predictMap)
  }

  /**
    * Get the background [[Booster]] object of this model.
    *
    * @return the [[Booster]]
    */
  def getBooster: Booster = this.booster

  /**
    * Streaming predict.
    * Predict with the given [[DataStream]] of [[DMatrix]]
    *
    * @param testData The [[DataStream]] of [[DMatrix]] to be predicted.
    * @param numberOfParallelism the number of parallelism of the prediction
    * @return the prediction result [[DataStream]] of Array[Array[Float]\]
    */
  def predict(testData: DataStream[DMatrix],
              numberOfParallelism: Int
             ): DataStream[Array[Array[Float]]] = {
    val input = if (testData.parallelism != numberOfParallelism) {
      testData.rebalance.map(x => x).setParallelism(numberOfParallelism)
    } else {
      testData
    }
    input.map(this.booster.predict(_)).setParallelism(numberOfParallelism)
  }

  /**
    * Streaming predict with key.
    * Predict with the given [[DataStream]] of ([[DMatrix]], Array[K]).
    * The [[DataStream]] contains the test data and a K type key for the data too
    * in order to make the identification of the prediction result easier.
    *
    * @param testData the [[DataStream]] of ([[DMatrix]], Array[K]) to be predicted
    * @param numberOfParallelism the number of parallelism of the prediction
    * @tparam K the type of the key
    * @return the prediction result [[DataStream]] of Array[(Array[Float], K)] with the key
    */
  def predictWithId[K](testData: DataStream[(DMatrix, Array[K])],
                       numberOfParallelism: Int
                      ): DataStream[Array[(Array[Float], K)]] = {
    implicit val typeInfo = TypeInformation.of(classOf[(DMatrix, Array[K])])
    implicit val typeInfo2 = TypeInformation.of(classOf[Array[(Array[Float], K)]])
    val input = if (testData.parallelism != numberOfParallelism) {
      testData.rebalance.map(x => x).setParallelism(numberOfParallelism)
    } else {
      testData
    }
    input.map(x => {
      this.booster.predict(x._1).zip(x._2)
    }).setParallelism(numberOfParallelism)
  }

}

/**
  * Helper function to load model.
  */
object XGBoostModel {

  import ml.dmlc.xgboost4j.scala.{XGBoost => XGBoostScala}
  /**
    * Load XGBoost model from path. it is prepared to both local/ and Hadoop filesystem.
    *
    * @param modelPath      The path of the model
    * @param fromHadoopFile if it is true the model path will be deal as Hadoop file.
    *                       By default it use hadoop filesystem API.
    * @return The loaded model
    */
  def loadModelFromFile(modelPath: String, fromHadoopFile: Boolean = true): XGBoostModel =
    new XGBoostModel(
      if (fromHadoopFile) {
        XGBoostScala.loadModel(FileSystem
          .get(new Configuration)
          .open(new Path(modelPath)))
      } else {
        XGBoostScala.loadModel(modelPath)
      })

}

