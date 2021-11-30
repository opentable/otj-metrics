/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.metrics.graphite;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import javax.management.MBeanServer;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteSender;
import com.google.common.collect.ImmutableMap;
import com.mogwee.executors.Executors;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.concurrent.OTExecutors;
import com.opentable.metrics.DefaultMetricsConfiguration;
import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.ServiceInfo;

/**
 * Tests for Graphite connection failures, reconnection, etc.
 */
public class GraphiteConnectTest {
    private static final Logger LOG = LoggerFactory.getLogger(GraphiteConnectTest.class);
    private static final int DEBUG_PAUSE_TEST_SLEEP_MILLIS = 0;

    @Test
    public void sendTest() throws IOException, InterruptedException {
        final TcpServer server = new TcpServer(0);
        final int port = server.start();

        final Duration reportingPeriod = Duration.ofMillis(200);
        final Map<String, Object> props = new ImmutableMap.Builder<String, Object>()
                .put("INSTANCE_NO", "0")
                .put("OT_ENV_TYPE", "dev")
                .put("OT_ENV_LOCATION", "somewhere")
                .put("ot.graphite.graphite-host", "localhost")
                .put("ot.graphite.graphite-port", Integer.toString(port))
                .put("ot.graphite.reporting-period", reportingPeriod.toString())
                .build();

        final SpringApplication app = new SpringApplication(TestConfiguration.class);
        app.setDefaultProperties(props);
        final ApplicationContext context = app.run();
        final BeanFactory factory = context.getAutowireCapableBeanFactory();
        final Counter detectedConnectionFailures = findConnectionFailureCounter(factory);
        final MetricRegistry metricRegistry = factory.getBean(MetricRegistry.class);
        Assert.assertFalse(server.contains("pre adding counter","foo.bar.baz"));
        final Counter counter = metricRegistry.counter("foo.bar.baz");
        counter.inc();
        counter.inc();
        counter.inc();
        // Wait for data relay.
        Thread.sleep(reportingPeriod.plusMillis(400).toMillis());
        Assert.assertTrue(server.getBytesRead() > 0);
        Assert.assertTrue(server.contains("post adding counter", "foo.bar.baz"));
        SpringApplication.exit(context, () -> 0);
        server.stopClean();
        Assert.assertEquals(0, detectedConnectionFailures.getCount());
    }

    @Test
    public void reconnectTest() throws IOException, InterruptedException {
        final TcpServer server = new TcpServer(0);
        final int port = server.start();

        final Duration reportingPeriod = Duration.ofSeconds(1);
        final Map<String, Object> props = new ImmutableMap.Builder<String, Object>()
                .put("INSTANCE_NO", "0")
                .put("OT_ENV_TYPE", "dev")
                .put("OT_ENV_LOCATION", "somewhere")
                .put("ot.graphite.graphite-host", "localhost")
                .put("ot.graphite.graphite-port", Integer.toString(port))
                .put("ot.graphite.reporting-period", reportingPeriod.toString())
                .build();

        final SpringApplication app = new SpringApplication(TestConfiguration.class);
        app.setDefaultProperties(props);
        final ApplicationContext context = app.run();
        final BeanFactory factory = context.getAutowireCapableBeanFactory();
        final Counter detectedConnectionFailures = findConnectionFailureCounter(factory);
        final Counter connectionCloses = findConnectionCloseCounter(factory);
        final MetricRegistry metricRegistry = factory.getBean(MetricRegistry.class);
        final Counter counterBoo = metricRegistry.counter("wolf.nipple.chips");
        counterBoo.inc();
        counterBoo.inc();
        counterBoo.inc();


        // Wait for graphite to connect.
        Thread.sleep(reportingPeriod.multipliedBy(2).toMillis());
        Assert.assertTrue(server.getBytesRead() > 0);
        Assert.assertEquals(0, detectedConnectionFailures.getCount());
        server.contains("wolf created but otherwise default", "foo");
        server.stopClean();
        // Wait for connection failure.
        Thread.sleep(reportingPeriod.multipliedBy(3).toMillis());
        Assert.assertTrue(connectionCloses.getCount() > 0 || detectedConnectionFailures.getCount() > 0);

        // New server on same port--wrapper should orchestrate a reconnect.
        final TcpServer server2 = new TcpServer(port);
        Assert.assertEquals(server2.start(), port);

        // Wait for reconnect.
        Thread.sleep(reportingPeriod.toMillis());

        Assert.assertFalse(server2.contains("second server","foo.bar.baz"));

        final Counter counter = metricRegistry.counter("foo.bar.baz");
        counter.inc();
        counter.inc();
        counter.inc();
        metricRegistry.counter("fluffy").inc();

        // Wait for data relay.
        Thread.sleep(reportingPeriod.multipliedBy(3).toMillis());

        Assert.assertTrue(server2.getBytesRead() > 0);
        Assert.assertTrue(server2.contains("foobar where are you??","foo.bar.baz"));
        SpringApplication.exit(context, () -> 0);
        server2.stopClean();
    }

    @Ignore("Connection not working locally")
    @Test
    public void senderWrapperTest() throws Exception {
        final TcpServer server = new TcpServer(0);
        final int port = server.start();

        final Duration reportingPeriod = Duration.ofSeconds(1);
        final Map<String, Object> props = new ImmutableMap.Builder<String, Object>()
                .put("INSTANCE_NO", "0")
                .put("OT_ENV_TYPE", "dev")
                .put("OT_ENV_LOCATION", "somewhere")
                .put("ot.graphite.graphite-host", "localhost")
                .put("ot.graphite.graphite-port", Integer.toString(port))
                .put("ot.graphite.reporting-period", reportingPeriod.toString())
                .build();

        final SpringApplication app = new SpringApplication(TestConfiguration.class);
        app.setDefaultProperties(props);
        final ApplicationContext context = app.run();
        final BeanFactory factory = context.getAutowireCapableBeanFactory();
        final GraphiteSender sender = factory.getBean(GraphiteSender.class);

        // Emulate internal logic of GraphiteReporter
        emulateGraphiteReporterSend(sender,"test1", "1", 1234);
        Thread.sleep(reportingPeriod.toMillis());
        Assert.assertTrue(server.contains("sending raw bytes","test1 1 1234"));

        server.stopClean();
        // Wait for connection failure.
        Thread.sleep(reportingPeriod.multipliedBy(3).toMillis());

        try {
            emulateGraphiteReporterSend(sender,"test2", "2", 2345);
            sender.flush();
        } catch (IOException expected) {
            Assert.assertTrue(expected instanceof ConnectException);
        }

        // New server on same port--wrapper should orchestrate a reconnect.
        final TcpServer server2 = new TcpServer(port);
        Assert.assertEquals(server2.start(), port);

        Assert.assertFalse(server2.contains("Now we've restarted, test2 should be there","test2"));
        emulateGraphiteReporterSend(sender,"test3", "3", 3456);
        sender.flush();
        Thread.sleep(reportingPeriod.toMillis());

        Assert.assertTrue(server2.getBytesRead() > 0);
        Assert.assertTrue(server2.contains("and postconnect continues to run smooth","test3"));

        final Counter connectionCloses = findConnectionCloseCounter(factory);
        SpringApplication.exit(context, () -> 0);
        server2.stopClean();
        Assert.assertNotEquals(0, connectionCloses.getCount());
    }

    private static Counter findConnectionFailureCounter(final BeanFactory factory) {
        return (Counter) factory.getBean(MetricRegistry.class).getMetrics()
                .get(GraphiteConfiguration.PREFIX + GraphiteSenderWrapper.DETECTED_CONNECTION_FAILURES);
    }

    private static Counter findConnectionCloseCounter(final BeanFactory factory) {
        return (Counter) factory.getBean(MetricRegistry.class).getMetrics()
                .get(GraphiteConfiguration.PREFIX + GraphiteSenderWrapper.CONNECTION_CLOSE);
    }

    private void emulateGraphiteReporterSend(GraphiteSender graphite, String name, String value, long timestamp)  {
        // STRIPPED DOWN VERSION OF GRAPHITEREPORT.REPORT METHOD
        try {
            graphite.connect();
            graphite.send(name, value, timestamp);
            graphite.flush();
        } catch (IOException e) {
            LOG.warn("Unable to report to Graphite", graphite, e);
        } finally {
            try {
                graphite.close();
            } catch (IOException e1) {
                LOG.warn("Error closing Graphite", graphite, e1);
            }
        }

    }

    private static class TcpServer {
        private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);
        private final int desiredPort;
        private final LongAdder bytesRead = new LongAdder();
        private final AtomicBoolean running = new AtomicBoolean();
        private final StringBuilder sb = new StringBuilder();
        private ServerSocket sock;
        private ExecutorService exec;

        private TcpServer(final int desiredPort) {
            this.desiredPort = desiredPort;
        }

        private int start() throws IOException {
            sock = new ServerSocket(desiredPort);
            final int port = sock.getLocalPort();
            exec = Executors.newSingleThreadExecutor("tcp-server");
            running.set(true);
            exec.submit(this::run);
            LOG.info("listening on port {}", port);
            return port;
        }

        private void killClientHandler() throws InterruptedException {
            OTExecutors.shutdownAndAwaitTermination(exec, Duration.ofSeconds(1));
            exec = null;
        }

        private void stopClean() throws InterruptedException, IOException {
            running.set(false);
            killClientHandler();
            sock.close();
        }

        private void stopDirty() throws InterruptedException {
            killClientHandler();
        }

        private long getBytesRead() {
            return bytesRead.longValue();
        }

        private boolean contains(final String scenario, final String s) {
            LOG.error(scenario + " :: " + "CONTAINS " + s +  "=" + sb.toString().contains(s) + "\r\n\r\n" + sb.toString());
            try {
                Thread.sleep(DEBUG_PAUSE_TEST_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return sb.toString().contains(s);
        }


        private void run() {
            LOG.info("starting");
            try {
                // The original test code exited after
                // client disconnected, but this ignores the fact
                // that DW now includes a reconnect internally after each send.
                // Now we only exit completely if the server is shutdown,
                // by adding this extra outer loop
                while (running.get()) {
                    final Socket client = sock.accept();
                    LOG.info("client Connected!");
                    final InputStream input = client.getInputStream();
                    final byte[] buf = new byte[16384];

                    while (running.get()) {
                        final int n = input.read(buf);
                        if (n == -1) {
                            LOG.info("client disconnected");
                            break;
                        } else {
                            bytesRead.add(n);
                            final String asString = new String(buf, 0, n, StandardCharsets.US_ASCII);
                            sb.append(asString);
                            //LOG.info("read {} bytes\n{}", n, asString);
                        }
                        Thread.sleep(Duration.ofMillis(100).toMillis());
                    }

                    client.shutdownOutput();
                    client.shutdownInput();
                    client.close();
                }

            } catch (final IOException e) {
                throw new RuntimeException(e);
            } catch (final InterruptedException e) {
                LOG.info("interrupted, bailing");
                Thread.currentThread().interrupt();
            } finally {
                LOG.info("exiting");
            }
        }
    }

    @Configuration
    @Import({
            AppInfo.class,
            EnvInfo.class,
            DefaultMetricsConfiguration.class,
    })
    static class TestConfiguration {
        @Bean
        public MBeanServer getMBeanServer() {
            return ManagementFactory.getPlatformMBeanServer();
        }

        @Bean
        public ServiceInfo getServiceInfo() {
            return new ServiceInfo() {
                @Override
                public String getName() {
                    return "test-service-name";
                }
            };
        }
    }
}
