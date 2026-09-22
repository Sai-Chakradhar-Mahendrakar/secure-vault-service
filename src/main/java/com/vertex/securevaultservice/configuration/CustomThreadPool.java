package com.vertex.securevaultservice.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class CustomThreadPool {
    private static final ThreadPoolTaskExecutor COMPUTATION_THREAD_POOL;
    private static final ThreadPoolTaskExecutor DB_THREAD_POOL;
    private static final ThreadPoolTaskExecutor HTTP_CLIENT_THREAD_POOL;

    static {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        COMPUTATION_THREAD_POOL = getExecutor("computationThread-", cpuCount, cpuCount * 2, 100, 30);
        DB_THREAD_POOL = getExecutor("databaseThread-", cpuCount, cpuCount * 4, 100, 30);
        HTTP_CLIENT_THREAD_POOL = getExecutor("httpClientThread-", 20, 50, 200, 60);
    }

    private static ThreadPoolTaskExecutor getExecutor(
            String threadNamePrefix,
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            int keepAliveSeconds
    ) {
        ThreadPoolTaskExecutor customThreadPool = new ThreadPoolTaskExecutor();
        customThreadPool.setCorePoolSize(corePoolSize);
        customThreadPool.setMaxPoolSize(maxPoolSize);
        customThreadPool.setThreadNamePrefix(threadNamePrefix);
        customThreadPool.setQueueCapacity(queueCapacity);
        customThreadPool.setKeepAliveSeconds(keepAliveSeconds);

        customThreadPool.setWaitForTasksToCompleteOnShutdown(true);
        customThreadPool.setAwaitTerminationSeconds(30);

        customThreadPool.initialize();
        return customThreadPool;
    }

    public static Executor getDatabaseExecutor() {
        return DB_THREAD_POOL;
    }

    public static Executor getComputationExecutor() {
        return COMPUTATION_THREAD_POOL;
    }

    public static Executor getHTTPExecutor() {
        return HTTP_CLIENT_THREAD_POOL;
    }
}
