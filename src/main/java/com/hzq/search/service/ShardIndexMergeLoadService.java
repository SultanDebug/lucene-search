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
        log.info("分片查询开始：查询类型-{},查询索引-{},搜索词-{}", type, index, query);
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
            //String finalQuery = query;
            List<Supplier<List<Map<String, String>>>> list = indexLoadServiceMap.values().stream()
                    .map(service -> (Supplier<List<Map<String, String>>>) () -> service.getRight().shardSearch(fieldMap, query, filter, totle, type, explain))
                    .collect(Collectors.toList());

            List<List<Map<String, String>>> collects = AsynUtil.submitToListBySupplier(executorService, list);

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
}
