This component provides a number of metrics for your application,
including metrics for the embedded Jetty component, the JVM, and a
rudimentary health-check.  In addition, these metrics are automatically
sent to Graphite, depending on your application's configuration.

In order to enable Graphite reporting of all these metrics, add the
following property to your configuration.

    ot.graphite.graphite-host

For a listing of what [Carbon][1] hosts to connect to in the region in
which your application is deployed, see [the internal DNS entries][2].

The default is for it to be unset, and in this case, Graphite reporting
will not occur.  You may also customize it further with the following
two configuration properties.

    ot.graphite.graphite-port
    ot.graphite.reporting-period-in-seconds

As in [the demo server][3], ensure that your server class returns an
instance of the [`BasicRestHttpServerTemplateModule`][4] from its
`getServerTemplateModule` method, and the metrics will automatically be
set up for you.

In order to take advantage of [NMT][5] metrics, you will need to enable
a JVM argument:

    -XX:NativeMemoryTracking=summary

If this JVM argument is not present, the NMT metrics will not be
tracked (but a warning will be logged via the `otj-jvm` library).

The prefix used for Graphite metrics is as follows.

    app_metrics.service-name[-env-flavor].env-type.env-location.instance-x

Where the `service-name` is as you've defined it in your application,
the environment type, location, and flavor are as described in [the Java
Services documentation][6], and the instance number is as declared by
Singularity.

Underneath this prefix namespace, this library defines its metrics with
the following namespaces.

- Jetty: `org.eclipse.jetty.server.handler`
- JVM: `jvm`

You may optionally enable [the Dropwizard Metrics AdminServlet][7] by
installing the `MetricsHttpModule`.  By default, it will make the
servlet available at `/metrics`, but you may customize the path at which
the metrics are available by calling the module constructor with a
differing path.  See the source code for the format.

Historical Note
---------------
Prior to version `1.11.2` of `otj-server`,
`BasicRestHttpServerTemplateModule` would install the
`MetricsHttpModule` at `/metrics` _by default_ in your application.  It
no longer implicitly installs this module.

[1]: https://github.com/graphite-project/carbon
[2]: https://github.com/opentable/ot-dns/blob/master/internal/otenv.com.db
[3]: https://github.com/opentable/service-demo
[4]: https://github.com/opentable/otj-server/blob/master/templates/src/main/java/com/opentable/server/templates/BasicRestHttpServerTemplateModule.java
[5]: https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr007.html
[6]: https://wiki.otcorp.opentable.com/display/CP/ArchTeam+Java+Services
[7]: http://metrics.dropwizard.io/3.1.0/manual/servlets/#adminservlet
