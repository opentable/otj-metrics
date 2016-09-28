package com.opentable.metrics.graphite;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
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
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableMap;
import com.mogwee.executors.Executors;

import org.junit.Assert;
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
    @Test
    public void sendTest() throws IOException, InterruptedException {
        final TcpServer server = new TcpServer();
        final int port = server.start();

        final Map<String, Object> props = new ImmutableMap.Builder<String, Object>()
                .put("INSTANCE_NO", "0")
                .put("OT_ENV_TYPE", "dev")
                .put("OT_ENV_LOCATION", "somewhere")
                .put("ot.graphite.graphite-host", "localhost")
                .put("ot.graphite.graphite-port", Integer.toString(port))
                .put("ot.graphite.reporting-period", "PT0.2S")
                .build();

        final SpringApplication app = new SpringApplication(TestConfiguration.class);
        app.setDefaultProperties(props);
        final ApplicationContext context = app.run();
        final BeanFactory factory = context.getAutowireCapableBeanFactory();
        final MetricRegistry metricRegistry = factory.getBean(MetricRegistry.class);
        final Counter counter = metricRegistry.counter("foo.bar.baz");
        counter.inc();
        counter.inc();
        counter.inc();
        Thread.sleep(Duration.ofMillis(300).toMillis());
        Assert.assertTrue(server.getBytesRead() > 0);
        SpringApplication.exit(context, () -> 0);
        server.stopClean();
    }

    private static class TcpServer {
        private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);
        private LongAdder bytesRead = new LongAdder();
        private AtomicBoolean running = new AtomicBoolean();
        private ServerSocket sock;
        private ExecutorService exec;

        private int start() throws IOException {
            sock = new ServerSocket(0);
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

        private void run() {
            LOG.info("starting");
            try {
                final Socket client = sock.accept();
                final InputStream input = client.getInputStream();
                final byte[] buf = new byte[4096];
                while (running.get()) {
                    final int n = input.read(buf);
                    if (n == -1) {
                        LOG.info("client disconnected");
                        break;
                    } else {
                        bytesRead.add(n);
                        LOG.info("read {} bytes\n{}", n, new String(buf, 0, n, StandardCharsets.US_ASCII));
                    }
                    Thread.sleep(Duration.ofMillis(100).toMillis());
                }
                client.shutdownOutput();
                client.shutdownInput();
                client.close();
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
        public MetricRegistry getMetricRegistry() {
            return new MetricRegistry();
        }

        @Bean
        public HealthCheckRegistry getHealthCheckRegistry() {
            return new HealthCheckRegistry();
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
