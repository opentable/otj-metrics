package com.opentable.metrics.health;

import com.codahale.metrics.health.HealthCheck;

class JvmHealthCheck extends HealthCheck
{
    @Override
    protected Result check() throws Exception
    {
        // Currently just checks that the JVM is running *g*
        return Result.healthy();
    }
}
