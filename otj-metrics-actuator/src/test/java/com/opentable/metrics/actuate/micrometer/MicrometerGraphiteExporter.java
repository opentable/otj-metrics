package com.opentable.metrics.actuate.micrometer;

import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;

import com.opentable.metrics.actuate.AbstractTest;

public class MicrometerGraphiteExporter extends AbstractTest {

  @Value("${management.metrics.export.defaults.enabled:true}")
  private boolean graphiteEnabled;

  @Test
  public void micrometerGraphiteExporterShouldBeDisabled() {
    assertFalse(graphiteEnabled);
  }

}
