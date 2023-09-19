package com.hzq.search.schedule;

import com.hzq.common.dto.ResultResponse;
import com.hzq.search.controller.ShardController;
import com.hzq.search.log.CommonConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.UUID;

/**
 * @author Huangzq
 * @description
 * @date 2023/6/8 10:45
 */
@Component
@EnableScheduling
@Slf4j
public class IndexSchedule {

    @Autowired
    private ShardController shardController;

    /**
     * 1.索引更新任务从controller开始，目前锁放在controller层
     * 2.频率：每天凌晨0点开始
     *
     * @author Huangzq
     * @date 2023/6/8 11:26
     */
    @Scheduled(cron = "0 0 0 * * ? ")
    public void indexUpdate() {
        try {
            MDC.put(CommonConstants.TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
            log.info("索引更新任务开始");
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            ResultResponse<List<String>> enterprise = shardController.create("enterprise");
            stopWatch.stop();
            log.info("索引更新任务成功{}，{}", stopWatch.getLastTaskTimeMillis(), enterprise);
        } catch (Exception e) {
            log.error("索引更新任务失败", e);
        } finally {
            MDC.remove(CommonConstants.TRACE_ID);
        }
    }
}
