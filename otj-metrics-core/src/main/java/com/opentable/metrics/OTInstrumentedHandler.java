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
package com.opentable.metrics;

import static com.codahale.metrics.MetricRegistry.name;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.Timer;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.AsyncContextEvent;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Copy of Dropwizard Metrics {@link com.codahale.metrics.jetty9.InstrumentedHandler} which correctly records
 * metrics about synchronous, asynchronous-suspended, and asynchronous-non-suspended requests.
 *
 * In any place where logic in this class does not match the Dropwizard InstrumentedHandler the logic was changed
 * to match Jetty's own StatisticsHandler, which happens to correctly record metrics for all asynchronous requests.
 */
@SuppressFBWarnings("BC_UNCONFIRMED_CAST")
public class OTInstrumentedHandler extends HandlerWrapper {
    private final MetricRegistry metricRegistry;

    private String name;
    private final String prefix;

    // the requests handled by this handler, excluding active
    private Timer requests;

    // the number of dispatches seen by this handler, excluding active
    private Timer dispatches;

    // the number of active requests
    private Counter activeRequests;

    // the number of active dispatches
    private Counter activeDispatches;

    // the number of requests currently suspended.
    private Counter activeSuspended;

    // the number of requests that have been asynchronously dispatched
    private Meter asyncDispatches;

    // the number of requests that expired while suspended
    private Meter asyncTimeouts;

    private Meter[] responses;

    private Timer getRequests;
    private Timer postRequests;
    private Timer headRequests;
    private Timer putRequests;
    private Timer deleteRequests;
    private Timer optionsRequests;
    private Timer traceRequests;
    private Timer connectRequests;
    private Timer moveRequests;
    private Timer otherRequests;

    private AsyncListener listener;

    /**
     * Create a new instrumented handler using a given metrics registry.
     *
     * @param registry the registry for the metrics
     */
    public OTInstrumentedHandler(MetricRegistry registry) {
        this(registry, null);
    }

    /**
     * Create a new instrumented handler using a given metrics registry.
     *
     * @param registry the registry for the metrics
     * @param prefix   the prefix to use for the metrics names
     */
    public OTInstrumentedHandler(MetricRegistry registry, String prefix) {
        this.metricRegistry = registry;
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final String prefix = this.prefix == null ? name(getHandler().getClass(), name) : name(this.prefix, name);

        this.requests = metricRegistry.timer(name(prefix, "requests"));
        this.dispatches = metricRegistry.timer(name(prefix, "dispatches"));

        this.activeRequests = metricRegistry.counter(name(prefix, "active-requests"));
        this.activeDispatches = metricRegistry.counter(name(prefix, "active-dispatches"));
        this.activeSuspended = metricRegistry.counter(name(prefix, "active-suspended"));

        this.asyncDispatches = metricRegistry.meter(name(prefix, "async-dispatches"));
        this.asyncTimeouts = metricRegistry.meter(name(prefix, "async-timeouts"));

        this.responses = new Meter[]{
                metricRegistry.meter(name(prefix, "1xx-responses")), // 1xx
                metricRegistry.meter(name(prefix, "2xx-responses")), // 2xx
                metricRegistry.meter(name(prefix, "3xx-responses")), // 3xx
                metricRegistry.meter(name(prefix, "4xx-responses")), // 4xx
                metricRegistry.meter(name(prefix, "5xx-responses"))  // 5xx
        };

        this.getRequests = metricRegistry.timer(name(prefix, "get-requests"));
        this.postRequests = metricRegistry.timer(name(prefix, "post-requests"));
        this.headRequests = metricRegistry.timer(name(prefix, "head-requests"));
        this.putRequests = metricRegistry.timer(name(prefix, "put-requests"));
        this.deleteRequests = metricRegistry.timer(name(prefix, "delete-requests"));
        this.optionsRequests = metricRegistry.timer(name(prefix, "options-requests"));
        this.traceRequests = metricRegistry.timer(name(prefix, "trace-requests"));
        this.connectRequests = metricRegistry.timer(name(prefix, "connect-requests"));
        this.moveRequests = metricRegistry.timer(name(prefix, "move-requests"));
        this.otherRequests = metricRegistry.timer(name(prefix, "other-requests"));

        metricRegistry.register(name(prefix, "percent-4xx-1m"), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(responses[3].getOneMinuteRate(),
                        requests.getOneMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-4xx-5m"), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(responses[3].getFiveMinuteRate(),
                        requests.getFiveMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-4xx-15m"), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(responses[3].getFifteenMinuteRate(),
                        requests.getFifteenMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-5xx-1m"), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(responses[4].getOneMinuteRate(),
                        requests.getOneMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-5xx-5m"), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(responses[4].getFiveMinuteRate(),
                        requests.getFiveMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-5xx-15m"), new RatioGauge() {
            @Override
            public Ratio getRatio() {
                return Ratio.of(responses[4].getFifteenMinuteRate(),
                        requests.getFifteenMinuteRate());
            }
        });

        this.listener = new AsyncListener() {
            // Diff: removed locally scoped startTime variable

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                asyncTimeouts.mark();
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
                // Diff: Removed local startTime variable
                event.getAsyncContext().addListener(this);
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
            }

            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                // Diff: Rewrote onComplete method body to call new #updateResponses and decrement activeSuspended correctly
                final HttpChannelState state = ((AsyncContextEvent)event).getHttpChannelState();

                final Request request = state.getBaseRequest();
                final long elapsed = System.currentTimeMillis() - request.getTimeStamp();

                updateResponses(request, elapsed);

                activeSuspended.dec();
            }

        };
    }

    /**
     * Jetty processes new requests and resumed (previously suspended) requests by the following logic:
     *
     * - New requests (isInitial() == true) are dispatched to the handler.
     * - Previously suspended requests (isInitial() == false) are redispatched to the handler upon being resumed.
     *
     * The handle method receives a dispatched request in one of these two states, attempts to handle it, and then
     * finally updates the appropriate metrics.
     *
     * After the handle method is called, the request is left in one of three states:
     *
     * - Requests that got suspended during handling (isSuspended() == true) get attached with an AsyncListener,
     *   do not update response metrics, and will later be redispatched to the handler whenever processing is resumed.
     * - New requests that completed without being suspended and which were not previously suspended
     *   (isSuspended() == false AND isInitial() == true) will trigger the update of response metrics in-line.
     * - Previously suspended requests that finally got completely handled (isSuspended() == true AND isInitial() == false)
     *   will be picked up by the attached AsyncListener's onComplete method, and trigger the update of response metrics.
     */
    @Override
    public void handle(String path,
                       Request request,
                       HttpServletRequest httpRequest,
                       HttpServletResponse httpResponse) throws IOException, ServletException {

        activeDispatches.inc();

        final long start;
        final HttpChannelState state = request.getHttpChannelState();
        if (state.isInitial()) {
            // New request
            activeRequests.inc();
            start = request.getTimeStamp();
            // Diff: Removed calling state.addListener(listener)
        }
        else {
            // Resumed request
            start = System.currentTimeMillis();
            // Diff: removed decrementing activeSuspended
            // Diff: Removed conditional around call to mark asyncDispatches
            asyncDispatches.mark();
        }

        try {
            super.handle(path, request, httpRequest, httpResponse);
        }
        finally {
            final long now = System.currentTimeMillis();
            final long dispatched = now - start;

            activeDispatches.dec();
            dispatches.update(dispatched, TimeUnit.MILLISECONDS);

            if (state.isSuspended()) {
                // Request that got suspended during handling

                // Diff: Moved call to increment activeSuspended inside conditional check that state.isInitial
                // Diff: Added call to state.addListener(listener) inside conditional
                if (state.isInitial()) {
                    state.addListener(listener);
                    activeSuspended.inc();
                }
            }
            else if (state.isInitial()) {
                // New request that completed and was not previously suspended
                 updateResponses(request, dispatched);
            }
            // Resumed request that finally completed; handled by listener#onComplete method
        }
    }

    private Timer requestTimer(String method) {
        final HttpMethod m = HttpMethod.fromString(method);
        if (m == null) {
            return otherRequests;
        } else {
            switch (m) {
                case GET:
                    return getRequests;
                case POST:
                    return postRequests;
                case PUT:
                    return putRequests;
                case HEAD:
                    return headRequests;
                case DELETE:
                    return deleteRequests;
                case OPTIONS:
                    return optionsRequests;
                case TRACE:
                    return traceRequests;
                case CONNECT:
                    return connectRequests;
                case MOVE:
                    return moveRequests;
                default:
                    return otherRequests;
            }
        }
    }

    private void updateResponses(Request request, long elapsed) {
        final int responseStatus;
        if (request.isHandled()) {
            responseStatus = request.getResponse().getStatus() / 100;
        }
        else {
            responseStatus = 4; // will end up with a 404 response sent by HttpChannel.handle
        }

        if (responseStatus >= 1 && responseStatus <= 5) {
            responses[responseStatus - 1].mark();
        }

        activeRequests.dec();

        // Diff: Changed method to pass in elapsed time instead of calculating it here with an incorrect start time
        requests.update(elapsed, TimeUnit.MILLISECONDS);
        requestTimer(request.getMethod()).update(elapsed, TimeUnit.MILLISECONDS);
    }
}
