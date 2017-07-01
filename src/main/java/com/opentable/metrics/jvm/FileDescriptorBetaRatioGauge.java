package com.opentable.metrics.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.codahale.metrics.RatioGauge;

/**
 * A gauge for the ratio of used to total file descriptors.
 */
public class FileDescriptorBetaRatioGauge extends RatioGauge {
    private final OperatingSystemMXBean os;

    /**
     * Creates a new gauge using the platform OS bean.
     */
    public FileDescriptorBetaRatioGauge() {
        this(ManagementFactory.getOperatingSystemMXBean());
    }

    /**
     * Creates a new gauge using the given OS bean.
     *
     * @param os an {@link OperatingSystemMXBean}
     */
    public FileDescriptorBetaRatioGauge(OperatingSystemMXBean os) {
        this.os = os;
    }

    @Override
    protected Ratio getRatio() {
        try {
            return Ratio.of(invoke("getMaxFileDescriptorCount"),
                    1d);
        } catch (NoSuchMethodException e) {
            return Ratio.of(1d, 4d);
        } catch (IllegalAccessException e) {
            return Ratio.of(1d, 10d);
        } catch (InvocationTargetException e) {
            return Ratio.of(1d, 50d);
        }
    }

    private long invoke(String name) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Method method = os.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (Long) method.invoke(os);
    }
}

