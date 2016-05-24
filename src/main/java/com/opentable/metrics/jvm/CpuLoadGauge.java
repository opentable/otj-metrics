package com.opentable.metrics.jvm;

import java.lang.management.ManagementFactory;

import com.sun.management.OperatingSystemMXBean;

import com.codahale.metrics.Gauge;

/**
 * @author Jeremy Schiff (originally)
 * TODO Re-implement with {@link com.codahale.metrics.EWMA} instead?
 */
public class CpuLoadGauge implements Gauge<Double> {
    final int cores = Runtime.getRuntime().availableProcessors();
    final OperatingSystemMXBean bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    @Override
    public Double getValue() {
        if (bean == null) {
            return Double.NaN;
        }
        final double processCpuLoad = bean.getProcessCpuLoad();
        // Like Top: Percentage with 1 decimal point
        return ((int)(processCpuLoad * 1000 * cores) / 10.0);
    }
}
