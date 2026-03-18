package com.mprs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the async thread pool used by ReconciliationEngine.
 *
 * The @Async("taskExecutor") annotation in ReconciliationEngine
 * references this bean by name.
 *
 * Pool sizing:
 * - corePoolSize  = 4  — always-alive threads, handle normal load
 * - maxPoolSize   = 10 — burst capacity for concurrent job submissions
 * - queueCapacity = 50 — jobs queued before rejection
 *
 * Thread naming (recon-1, recon-2 ...) makes logs easy to trace.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("recon-");
        executor.setWaitForTasksToCompleteOnShutdown(true);   // graceful shutdown
        executor.setAwaitTerminationSeconds(60);              // wait up to 60s on shutdown
        executor.initialize();
        return executor;
    }
}