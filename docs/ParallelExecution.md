How to Execute Steps in Parallel
=====

AIP is not an execution platform, so it does have any parallelism explicitly built in.
Instead, it can take advantage of whatever parallelism is offered by the JVM
environment.

A simple mechanism for enabling parallel execution is to implement Producer classes whose output type is a `Future[_]`. Downstream Producers that consume `Future` results can use `Await` to block on inputs if needed or use `Future.map()` to queue execution without blocking.

For distributed execution, support for running pipelines using [Apache Spark](https://spark.apache.org/) can be found in the [spark](../spark/README.md) sub-project.

