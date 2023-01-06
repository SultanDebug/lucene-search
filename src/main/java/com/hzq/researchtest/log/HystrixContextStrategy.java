package com.hzq.researchtest.log;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

import static com.hzq.researchtest.log.CommonConstants.TRACE_ID;


/**
 * hystrix上下文传递插件
 * 未解决fallback里的上下文传递
 *
 * @author Huangzq
 * @description
 * @date 2022/8/29 15:18
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "feign.hystrix",name = "enabled",havingValue = "true")
public class HystrixContextStrategy extends HystrixConcurrencyStrategy implements InitializingBean {
    @Override
    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        String traceId = MDC.get(TRACE_ID);
        return () -> {
            MDC.put(TRACE_ID, traceId);
            try {
                return callable.call();
            } finally {
                MDC.remove(TRACE_ID);
            }
        };
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        HystrixCommandExecutionHook commandExecutionHook = HystrixPlugins.getInstance().getCommandExecutionHook();
        HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance().getPropertiesStrategy();
        HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance().getEventNotifier();
        HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance().getMetricsPublisher();

        //重置
        HystrixPlugins.reset();

        HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
        HystrixPlugins.getInstance().registerCommandExecutionHook(commandExecutionHook);
        HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);
        HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
        HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
    }
}
