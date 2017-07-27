otj-metrics
===========

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
