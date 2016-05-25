This component provides a number of metrics for your application,
including metrics for the embedded Jetty component, the JVM, and a
rudimentary health-check.  In addition, these metrics are automatically
sent to Graphite, depending on your application's configuration.

In order to enable Graphite reporting of all these metrics, add the
following property to your configuration.

    ot.graphite.graphite-host

The default is for it to be unset, and in this case, Graphite reporting
will not occur.  You may also customize it further with the following
two configuration properties.

    ot.graphite.graphite-port
    ot.graphite.reporting-period-in-seconds

As in [the demo server][1], ensure that your server class returns an
instance of the [`BasicRestHttpServerTemplateModule`][2] from its
`getServerTemplateModule` method, and the metrics will automatically be
set up for you.

In order to take advantage of [NMT][3] metrics, you will need to enable
a JVM argument:

    -XX:NativeMemoryTracking=summary

If this JVM argument is not present, the NMT metrics will not be
tracked (but a warning will be logged via the `otj-jvm` library).

[1]: https://github.com/opentable/service-demo
[2]: https://github.com/opentable/otj-server/blob/master/templates/src/main/java/com/opentable/server/templates/BasicRestHttpServerTemplateModule.java
[3]: https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr007.html
