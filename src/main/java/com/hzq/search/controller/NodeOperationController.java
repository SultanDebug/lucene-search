package com.hzq.search.controller;

import com.alibaba.fastjson.JSON;
import com.hzq.search.service.ResultResponse;
import com.hzq.search.util.AsynUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.hzq.search.log.CommonConstants.TRACE_ID;

/**
 * 多节点操作接口
 *
 * @author Huangzq
 * @description
 * @date 2023/3/13 16:27
 */
@Api(tags = "多节点索引操作接口")
@RestController
@Slf4j
public class NodeOperationController {

    public static ConcurrentMap<String, Semaphore> SEMAPHORE_MAP = new ConcurrentHashMap<>();

    @ApiOperation(value = "多节点索引创建")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name = "index", value = "索引名", required = true)
    })
    @GetMapping(value = "/node/index-create")
    public ResultResponse<Map<String, String>> create(@RequestParam("index") String index) {
        Semaphore semaphore = SEMAPHORE_MAP.computeIfAbsent(index, s -> new Semaphore(1));
        if (semaphore.tryAcquire()) {
            try {
                return ResultResponse.success(syncData(index));
            } catch (Exception e) {
                log.error("节点索引生成失败", e);
                return ResultResponse.fail("101", "节点索引生成失败，稍后再试");
            } finally {
                semaphore.release();
            }
        } else {
            return ResultResponse.fail("101", "节点索引生成中，勿重复提交");
        }
    }

    @Resource
    private DiscoveryClient discoveryClient;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 同步实例数据线程池
     */
    private static ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(2, 10,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());

    private Map<String, String> syncData(String index) {
        Map<String, String> result = new HashMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //日志链路id衔接
        headers.set(TRACE_ID, MDC.get(TRACE_ID));
        List<ServiceInstance> instances = discoveryClient.getInstances("lucene-search");

        List<AsynUtil.TaskExecute> taskExecutes = instances.stream().map(instance -> (AsynUtil.TaskExecute) () -> {
            String url =
                    "http://" + instance.getHost() + ":" + instance.getPort() + "/shard/create" + "?index=" + index;
            log.info("实例{}同步开始", instance.getUri().toString());
            try {
                HttpEntity<String> httpEntity = new HttpEntity<>("", headers);
                ParameterizedTypeReference<ResultResponse<List<String>>> classType =
                        new ParameterizedTypeReference<ResultResponse<List<String>>>() {
                        };
                ResponseEntity<ResultResponse<List<String>>> forEntity = restTemplate.exchange(url, HttpMethod.GET,
                        httpEntity, classType);
                if (Objects.requireNonNull(forEntity.getBody()).getCode().equals("200")) {
                    log.info("调用成功{}", instance.getUri().toString());
                } else {
                    log.info("调用失败{}", instance.getUri().toString());
                }

                result.put(instance.getHost(), JSON.toJSONString(forEntity.getBody()));
            } catch (Exception e) {
                log.error("同步{}异常：{}", instance.getUri().toString(), e.getMessage());
            }
            log.info("实例{}同步结束", instance.getUri().toString());
        }).collect(Collectors.toList());

        try {
            AsynUtil.executeSync(EXECUTOR_SERVICE, taskExecutes);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return result;
    }
}