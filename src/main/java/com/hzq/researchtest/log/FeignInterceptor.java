package com.hzq.researchtest.log;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static com.hzq.researchtest.log.CommonConstants.TRACE_ID;

/**
 * feign调用拦截，上下文参数透传
 *
 * @author Huangzq
 * @description
 * @date 2022/8/29 15:28
 */
@Configuration
@Slf4j
public class FeignInterceptor {
    @Bean
    public RequestInterceptor headerInterceptor() {
        return requestTemplate -> {
            String traceId = MDC.get(TRACE_ID);
            if (!StringUtils.isEmpty(traceId)) {
                requestTemplate.header(TRACE_ID, traceId);
            } else {
                requestTemplate.header(TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
            }

        };
    }
}
