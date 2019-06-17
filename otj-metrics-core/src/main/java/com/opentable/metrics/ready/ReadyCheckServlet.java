package com.opentable.metrics.ready;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class ReadyCheckServlet extends HttpServlet {
    public abstract static class ContextListener implements ServletContextListener {
        /**
         * @return the {@link ReadyCheckRegistry} to inject into the servlet context.
         */
        protected abstract ReadyCheckRegistry getReadyCheckRegistry();

        /**
         * @return the {@link ExecutorService} to inject into the servlet context, or {@code null}
         * if the ready checks should be run in the servlet worker thread.
         */
        protected ExecutorService getExecutorService() {
            // don't use a thread pool by default
            return null;
        }

        /**
         * @return the {@link ReadyCheckFilter} that shall be used to filter ready checks,
         * or {@link ReadyCheckFilter#ALL} if the default should be used.
         */
        protected ReadyCheckFilter getReadyCheckFilter() {
            return ReadyCheckFilter.ALL;
        }

        @Override
        public void contextInitialized(ServletContextEvent event) {
            final ServletContext context = event.getServletContext();
            context.setAttribute(READY_CHECK_REGISTRY, getReadyCheckRegistry());
            context.setAttribute(READY_CHECK_EXECUTOR, getExecutorService());
        }

        @Override
        public void contextDestroyed(ServletContextEvent event) {
            // no-op
        }
    }

    public static final String READY_CHECK_REGISTRY = ReadyCheckServlet.class.getCanonicalName() + ".registry";
    public static final String READY_CHECK_EXECUTOR = ReadyCheckServlet.class.getCanonicalName() + ".executor";
    public static final String READY_CHECK_FILTER = ReadyCheckServlet.class.getCanonicalName() + ".readyCheckFilter";

    private static final long serialVersionUID = -8432996484889177321L;
    private static final String CONTENT_TYPE = "application/json";

    private transient ReadyCheckRegistry registry;
    private transient ExecutorService executorService;
    private transient ReadyCheckFilter filter;
    private transient ObjectMapper mapper;

    public ReadyCheckServlet() {
    }

    public ReadyCheckServlet(ReadyCheckRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();
        if (null == registry) {
            final Object registryAttr = context.getAttribute(READY_CHECK_REGISTRY);
            if (registryAttr instanceof ReadyCheckRegistry) {
                this.registry = (ReadyCheckRegistry) registryAttr;
            } else {
                throw new ServletException("Couldn't find a ReadyCheckRegistry instance.");
            }
        }

        final Object executorAttr = context.getAttribute(READY_CHECK_EXECUTOR);
        if (executorAttr instanceof ExecutorService) {
            this.executorService = (ExecutorService) executorAttr;
        }


        final Object filterAttr = context.getAttribute(READY_CHECK_FILTER);
        if (filterAttr instanceof ReadyCheckFilter) {
            filter = (ReadyCheckFilter) filterAttr;
        }
        if (filter == null) {
            filter = ReadyCheckFilter.ALL;
        }

        this.mapper = new ObjectMapper(); // TODO
    }

    @Override
    public void destroy() {
        super.destroy();
        registry.shutdown();
    }

    @Override
    protected void doGet(HttpServletRequest req,
                         HttpServletResponse resp) throws ServletException, IOException {
        final SortedMap<String, ReadyCheck.Result> results = runReadyChecks();
        resp.setContentType(CONTENT_TYPE);
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        if (results.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        } else {
            if (isAllReady(results)) {
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        try (OutputStream output = resp.getOutputStream()) {
            getWriter(req).writeValue(output, results);
        }
    }

    private ObjectWriter getWriter(HttpServletRequest request) {
        final boolean prettyPrint = Boolean.parseBoolean(request.getParameter("pretty"));
        if (prettyPrint) {
            return mapper.writerWithDefaultPrettyPrinter();
        }
        return mapper.writer();
    }

    private SortedMap<String, ReadyCheck.Result> runReadyChecks() {
        if (executorService == null) {
            return registry.runReadyChecks(filter);
        }
        return registry.runReadyChecks(executorService, filter);
    }

    private static boolean isAllReady(Map<String, ReadyCheck.Result> results) {
        for (ReadyCheck.Result result : results.values()) {
            if (!result.isReady()) {
                return false;
            }
        }
        return true;
    }
}
