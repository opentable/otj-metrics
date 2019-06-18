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
package com.opentable.metrics.ready;

import java.util.concurrent.ExecutorService;

import javax.inject.Named;
import javax.servlet.ServletContextListener;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.concurrent.ThreadPoolBuilder;
import com.opentable.concurrent.ThreadPoolConfig;

@Configuration
@Import({
        ReadyRegistrar.class,
})
public class ReadyConfiguration {
    public static final String READY_CHECK_PATH = "/infra/ready";
    public static final String READY_CHECK_POOL_NAME = "ready-check";

    /**
     * Create a ready check registry to register health checks with
     * @return ready check registry
     */
    @Bean
    public ReadyCheckRegistry getReadyCheckRegistry() {
        return new ReadyCheckRegistry();
    }

    @Bean
    @Named(READY_CHECK_POOL_NAME)
    public ThreadPoolBuilder getReadyCheckPoolBuilder() {
        // Used for running ready checks asynchronously. This normally
        // Should be low load anyway, so callerRuns is appropriate backPressure
        // into the servlet pool. An alternative would be an unbounded but fixed
        return ThreadPoolBuilder
                .shortTaskPool(READY_CHECK_POOL_NAME, 8)
                .withDefaultRejectedHandler(ThreadPoolConfig.RejectedHandler.CALLER_RUNS.getHandler());
    }

    @Bean(name="readyCheckServletContextListener")
    public ServletContextListener getServletContextListener(
            final ReadyCheckRegistry registry,
            @Named(READY_CHECK_POOL_NAME) final ExecutorService executor) {
        return new ReadyCheckContextListener(registry, executor);
    }

}
