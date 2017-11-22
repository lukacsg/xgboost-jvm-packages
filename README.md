# Flink extension of XGBoost
[![GitHub license](http://dmlc.github.io/img/apache2.svg)](./LICENSE)
This project is an extension and update of the [XGBoost//github.com/dmlc/xgboost) library.
Thids extension deals only with the Flink connectors in the JVM package for scala langauge.
The main purpose of this project is the implementaion of Flink XGBoost over the [streaming API](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/datastream_api.html) which follows the needs of the Flink community.
## What is New
### Extensions
The existing minimal Flink batch implementation is extended by more easy-to-use function and some new feature.
There is implemented the XGBoost connector over the streaming API.
### Update
The flink version is updated.

## Where to start
The Flink implementation:
[XGBoost4J-Flink](https://github.com/streamline-eu/xgboost-jvm-packages/tree/master/jvm-packages/xgboost4j-flink)

There are some examples for usage here:
[XGBoost4J Code Examples](https://github.com/streamline-eu/xgboost-jvm-packages/tree/master/jvm-packages/xgboost4j-example)

An example application for streaming and batch usage of this extension:
https://github.com/streamline-eu/xgboost-application
