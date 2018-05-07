otj-metrics
===========

2.6.12-SNAPSHOT
--
* Use non deprecated concurrent HashSet.

2.6.11
------

* Improves compatibility with Dropwizard Metrics 3.2.3

2.6.10
------

* Remove Jetty work queue limits

2.6.9
-----
* Spring Boot 2 / Spring 5.0.4

2.6.8
-----
* Reimplemented file descriptor metric set

2.6.7
-----

* spring 5 support

2.6.6
-----

* simplifies file descriptor instrumentation

2.6.5
-----

* more qtp adjustment

2.6.4
-----

* makes time in gc a histogram (OTPL-1967)

2.6.3
-----

* fixes work queue sizing

2.6.2
-----

* dramatically shorten default work queue
* added gc timing metrics

2.6.1
-----

* fixed bug omitting javadocs for OSS release

2.6.0
-----

* added mem free metrics, gc-mem metrics
* OSS
* BOTCHED RELEASE; SEE 2.6.1

2.5.0
-----

* atomic long gauge is now counting

2.4.4
-----

* metric set builder prefixes now must not end with a dot

2.4.3
-----
* ConcurrentHashMap and possible NPE fix to HealthController
* Logging (trace level) during HealthController

2.4.0
-----

* added MetricUtils.slidingTimer utility function

2.3.1
-----

* otj-metrics exposes response count by status code

2.3.0
-----

* improve behavior of MetricSetBuilder

2.2.4
-----

* replace FileDescriptorRatioGauge with custom FileDescriptorMetricSet
* improve MetricsSets tranformName funciton to return immutable data structure

2.2.3
-----

* re-instate unconditional hourly reconnect
  ELBs are a terrible product.  We really should never have used them.

2.2.2
-----

* resolve dns names on every connection attempt, again
* remove unconditional hourly reconnect

2.2.1
-----

* improve graphite reconnect behavior
