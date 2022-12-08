package com.hzq.researchtest.service.shardindex;

import com.bird.search.utils.AsynUtil;
import com.hzq.researchtest.service.shardindex.shard.ShardIndexLoadService;
import com.hzq.researchtest.service.shardindex.shard.ShardIndexService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/30 17:18
 */
//@Service
@Slf4j
public class ShardIndexMergeLoadService extends IndexCommonService {
    /**
     * Description:
     *  搜索入口，并发搜索所有分片
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:33
     */
    public Map<String, List<String>> search(String index, String query) {
        if (!this.checkIndex(index)) {
            log.warn("索引不存在{}", index);
            return Collections.emptyMap();
        }
        Map<Integer, Pair<ShardIndexService,ShardIndexLoadService>> indexLoadServiceMap = SHARD_INDEX_MAP.get(index);
        if (indexLoadServiceMap == null) {
            log.warn("索引不存在{}", index);
            return null;
        }
        long start = System.currentTimeMillis();
        try {
            List<String> fsList = new ArrayList<>();
            List<String> incrList = new ArrayList<>();

            List<Supplier<Map<String, List<String>>>> list = indexLoadServiceMap.values().stream()
                    .map(service -> (Supplier<Map<String, List<String>>>) () -> service.getRight().search(query))
                    .collect(Collectors.toList());

            List<Map<String, List<String>>> collects = AsynUtil.submitToListBySupplier(executorService, list);
            for (Map<String, List<String>> collect : collects) {
                List<String> fss = collect.get("fs");
                List<String> incrs = collect.get("incr");
                if (!CollectionUtils.isEmpty(fss)) {
                    fsList.addAll(fss);
                }

                if (!CollectionUtils.isEmpty(incrs)) {
                    incrList.addAll(incrs);
                }
            }

            /*for (Map.Entry<Integer, ShardIndexLoadService> entry : SHARD_LOAD_MAP.entrySet()) {
                Map<String, List<String>> search = entry.getValue().search(query);
                List<String> fss = search.get("fs");
                List<String> incrs = search.get("incr");
                if(!CollectionUtils.isEmpty(fss)){
                    fsList.addAll(fss);
                }

                if(!CollectionUtils.isEmpty(incrs)){
                    incrList.addAll(incrs);
                }
            }*/

            Map<String, List<String>> map = new HashMap<>();
            map.put("fs", fsList);
            map.put("incr", incrList);
            log.info("分片查询结束：{}", System.currentTimeMillis() - start);
            return map;
        } catch (Exception e) {
            log.error("查询失败：{}", e.getMessage(), e);
        }
        return new HashMap<>();
    }
}
