package com.hzq.search.service;

import com.hzq.search.config.FieldDef;
import com.hzq.search.service.shard.ShardIndexLoadService;
import com.hzq.search.service.shard.ShardIndexService;
import com.hzq.search.util.AsynUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Huangzq
 * @date 2022/11/30 17:18
 */
@Service
@Slf4j
public class ShardIndexMergeLoadService extends IndexCommonAbstract {
    /**
     * Description:
     * 搜索入口，并发搜索所有分片
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:33
     */
    public Map<String, Object> concurrentSearch(String index, String query, String filter, String type, Integer size, Integer page, Boolean explain) {
        log.info("分片查询开始：查询类型-{},查询索引-{},搜索词-{}，类型-{}，条件-{}", type, index, query, type, filter);
        if (!this.checkIndex(false, index)) {
            log.warn("索引不存在{}", index);
            return null;
        }
        //todo normal
        //query = StringTools.normalServerString(query);
        Map<String, FieldDef> fieldMap = indexConfig.getIndexMap().get(index).getFieldMap();
        Map<Integer, Pair<ShardIndexService, ShardIndexLoadService>> indexLoadServiceMap = SHARD_INDEX_MAP.get(index);
        if (indexLoadServiceMap == null) {
            log.warn("索引不存在{}", index);
            return null;
        }
        long start = System.currentTimeMillis();
        try {
            AtomicLong totle = new AtomicLong(0);
            List<Supplier<List<Map<String, String>>>> list = indexLoadServiceMap.values().stream()
                    .map(service -> (Supplier<List<Map<String, String>>>) () -> service.getRight().shardSearch(index, fieldMap, query, filter, totle, type, explain))
                    .collect(Collectors.toList());

            log.info("查询前==>任务数：{}，总线程数：{}， 活动线程数：{}， 排队线程数：{}， 执行完成线程数：{}"
                    , indexLoadServiceMap.values().size()
                    , EXECUTOR_SERVICE.getTaskCount()
                    , EXECUTOR_SERVICE.getActiveCount()
                    , EXECUTOR_SERVICE.getQueue().size()
                    , EXECUTOR_SERVICE.getCompletedTaskCount());
            List<List<Map<String, String>>> collects = AsynUtil.submitToListBySupplier(EXECUTOR_SERVICE, list);
            log.info("查询后==>总线程数：{}， 活动线程数：{}， 排队线程数：{}， 执行完成线程数：{}"
                    , EXECUTOR_SERVICE.getTaskCount()
                    , EXECUTOR_SERVICE.getActiveCount()
                    , EXECUTOR_SERVICE.getQueue().size()
                    , EXECUTOR_SERVICE.getCompletedTaskCount());

            List<Map<String, String>> res = collects.stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(() -> new TreeSet<>((o1, o2) -> {
                                Long d1 = Long.parseLong(o1.get("id"));
                                Long d2 = Long.parseLong(o2.get("id"));
                                return d1.compareTo(d2);
                            }))
                            , v -> v.stream().sorted((o1, o2) -> {
                                Double d1 = Double.parseDouble(o1.get("score"));
                                Double d2 = Double.parseDouble(o2.get("score"));
                                if (!d1.equals(d2)) {
                                    return d2.compareTo(d1);
                                } else {
                                    Double d3 = Double.parseDouble(o1.get("company_score"));
                                    Double d4 = Double.parseDouble(o2.get("company_score"));
                                    return d4.compareTo(d3);
                                }
                            }).collect(Collectors.toList())
                    ))
                    .stream().skip((long) page * size).limit(size)
                    .collect(Collectors.toList());

            long time = System.currentTimeMillis() - start;
            log.info("分片查询结束：数量-{},耗时-{},", totle, time);
            Map<String, Object> map = new HashMap<>();
            map.put("totle", totle);
            map.put("took", time);
            map.put("data", res);
            return map;
        } catch (Exception e) {
            log.error("查询失败：{}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Description:
     * 搜索入口，并发搜索所有分片
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:33
     */
    public List<List<Map<String, Object>>> concurrentInfo(String index) {
        log.info("分片信息查询开始：查询索引-{}", index);
        if (!this.checkIndex(false, index)) {
            log.warn("索引不存在{}", index);
            return null;
        }
        Map<Integer, Pair<ShardIndexService, ShardIndexLoadService>> indexLoadServiceMap = SHARD_INDEX_MAP.get(index);
        if (indexLoadServiceMap == null) {
            log.warn("索引不存在{}", index);
            return null;
        }
        long start = System.currentTimeMillis();
        try {
            List<Supplier<List<Map<String, Object>>>> list = indexLoadServiceMap.values().stream()
                    .map(service -> (Supplier<List<Map<String, Object>>>) () -> service.getRight().shardInfo(index))
                    .collect(Collectors.toList());

            List<List<Map<String, Object>>> collects = AsynUtil.submitToListBySupplier(EXECUTOR_SERVICE, list);
            long time = System.currentTimeMillis() - start;
            log.info("分片信息查询结束：数量-{},耗时-{},", collects.size(), time);
            return collects;
        } catch (Exception e) {
            log.error("查询失败：{}", e.getMessage(), e);
        }
        return null;
    }
}
