package com.opentable.metrics.ready;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * A registry for ready checks.
 */
public class ReadyCheckRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadyCheckRegistry.class);
    private static final int ASYNC_EXECUTOR_POOL_SIZE = 2;

    private final ConcurrentMap<String, ReadyCheck> readyChecks;
    private final List<ReadyCheckRegistryListener> listeners;
    private final ScheduledExecutorService asyncExecutorService;
    private final Object lock = new Object();

    /**
     * Creates a new {@link ReadyCheckRegistry}.
     */
    public ReadyCheckRegistry() {
        this(ASYNC_EXECUTOR_POOL_SIZE);
    }

    /**
     * Creates a new {@link ReadyCheckRegistry}.
     *
     * @param asyncExecutorPoolSize core pool size for async ready check executions
     */
    public ReadyCheckRegistry(int asyncExecutorPoolSize) {
        this(createExecutorService(asyncExecutorPoolSize));
    }

    /**
     * Creates a new {@link ReadyCheckRegistry}.
     *
     * @param asyncExecutorService executor service for async ready check executions
     */
    public ReadyCheckRegistry(ScheduledExecutorService asyncExecutorService) {
        this.readyChecks = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.asyncExecutorService = asyncExecutorService;
    }

    /**
     * Adds a {@link ReadyCheckRegistryListener} to a collection of listeners that will be notified on ready check
     * registration. Listeners will be notified in the order in which they are added. The listener will be notified of all
     * existing ready checks when it first registers.
     *
     * @param listener listener to add
     */
    public void addListener(ReadyCheckRegistryListener listener) {
        listeners.add(listener);
        for (Map.Entry<String, ReadyCheck> entry : readyChecks.entrySet()) {
            listener.onReadyCheckAdded(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Removes a {@link ReadyCheckRegistryListener} from this registry's collection of listeners.
     *
     * @param listener listener to remove
     */
    public void removeListener(ReadyCheckRegistryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Registers an application {@link ReadyCheck}.
     *
     * @param name        the name of the ready check
     * @param readyCheck the {@link ReadyCheck} instance
     */
    public void register(String name, ReadyCheck readyCheck) {
        ReadyCheck registered = null;
        synchronized (lock) {
            if (!readyChecks.containsKey(name)) {
                registered = readyCheck;
                readyChecks.put(name, registered);
            }
        }
        if (registered != null) {
            onReadyCheckAdded(name, registered);
        }
    }

    /**
     * Unregisters the application {@link ReadyCheck} with the given name.
     *
     * @param name the name of the {@link ReadyCheck} instance
     */
    public void unregister(String name) {
        ReadyCheck readyCheck;
        synchronized (lock) {
            readyCheck = readyChecks.remove(name);
        }
        if (readyCheck != null) {
            onReadyCheckRemoved(name, readyCheck);
        }
    }

    /**
     * Returns a set of the names of all registered ready checks.
     *
     * @return the names of all registered ready checks
     */
    public SortedSet<String> getNames() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(readyChecks.keySet()));
    }

    /**
     * Returns the {@link ReadyCheck} instance with a given name
     *
     * @param name the name of the {@link ReadyCheck} instance
     */
    public ReadyCheck getReadyCheck(String name) {
        return readyChecks.get(name);
    }

    /**
     * Runs the ready check with the given name.
     *
     * @param name the ready check's name
     * @return the result of the ready check
     * @throws NoSuchElementException if there is no ready check with the given name
     */
    public ReadyCheck.Result runReadyCheck(String name) throws NoSuchElementException {
        final ReadyCheck readyCheck = readyChecks.get(name);
        if (readyCheck == null) {
            throw new NoSuchElementException("No ready check named " + name + " exists");
        }
        return readyCheck.execute();
    }

    /**
     * Runs the registered ready checks and returns a map of the results.
     *
     * @return a map of the ready check results
     */
    public SortedMap<String, ReadyCheck.Result> runReadyChecks() {
        return runReadyChecks(ReadyCheckFilter.ALL);
    }

    /**
     * Runs the registered ready checks matching the filter and returns a map of the results.
     *
     * @param filter ready check filter
     * @return a map of the ready check results
     */
    public SortedMap<String, ReadyCheck.Result> runReadyChecks(ReadyCheckFilter filter) {
        final SortedMap<String, ReadyCheck.Result> results = new TreeMap<>();
        for (Map.Entry<String, ReadyCheck> entry : readyChecks.entrySet()) {
            final String name = entry.getKey();
            final ReadyCheck readyCheck = entry.getValue();
            if (filter.matches(name, readyCheck)) {
                final ReadyCheck.Result result = entry.getValue().execute();
                results.put(entry.getKey(), result);
            }
        }
        return Collections.unmodifiableSortedMap(results);
    }

    /**
     * Runs the registered ready checks in parallel and returns a map of the results.
     *
     * @param executor object to launch and track ready checks progress
     * @return a map of the ready check results
     */
    public SortedMap<String, ReadyCheck.Result> runReadyChecks(ExecutorService executor) {
        return runReadyChecks(executor, ReadyCheckFilter.ALL);
    }

    /**
     * Runs the registered ready checks matching the filter in parallel and returns a map of the results.
     *
     * @param executor object to launch and track ready checks progress
     * @param filter   Ready check filter
     * @return a map of the ready check results
     */
    public SortedMap<String, ReadyCheck.Result> runReadyChecks(ExecutorService executor, ReadyCheckFilter filter) {
        final Map<String, Future<ReadyCheck.Result>> futures = new HashMap<>();
        for (final Map.Entry<String, ReadyCheck> entry : readyChecks.entrySet()) {
            final String name = entry.getKey();
            final ReadyCheck readyCheck = entry.getValue();
            if (filter.matches(name, readyCheck)) {
                futures.put(name, executor.submit(readyCheck::execute));
            }
        }

        final SortedMap<String, ReadyCheck.Result> results = new TreeMap<>();
        for (Map.Entry<String, Future<ReadyCheck.Result>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (Exception e) {
                LOGGER.warn("Error executing ready check {}", entry.getKey(), e);
                results.put(entry.getKey(), ReadyCheck.Result.unready(e));
            }
        }

        return Collections.unmodifiableSortedMap(results);
    }


    private void onReadyCheckAdded(String name, ReadyCheck readyCheck) {
        for (ReadyCheckRegistryListener listener : listeners) {
            listener.onReadyCheckAdded(name, readyCheck);
        }
    }

    private void onReadyCheckRemoved(String name, ReadyCheck readyCheck) {
        for (ReadyCheckRegistryListener listener : listeners) {
            listener.onReadyCheckRemoved(name, readyCheck);
        }
    }

    /**
     * Shuts down the scheduled executor for async ready checks
     */
    public void shutdown() {
        asyncExecutorService.shutdown(); // Disable new ready checks from being submitted
        try {
            // Give some time to the current ready checks to finish gracefully
            if (!asyncExecutorService.awaitTermination(1, TimeUnit.SECONDS)) {
                asyncExecutorService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            asyncExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ScheduledExecutorService createExecutorService(int corePoolSize) {
        final ScheduledThreadPoolExecutor asyncExecutorService = new ScheduledThreadPoolExecutor(corePoolSize,
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("readycheck-async-executor-%d").build());
        asyncExecutorService.setRemoveOnCancelPolicy(true);
        return asyncExecutorService;
    }
}
