package com.hzq.researchtest.service.single;

import com.alibaba.fastjson.JSON;
import com.bird.search.utils.AsynUtil;
import com.hzq.researchtest.config.FieldDef;
import com.hzq.researchtest.service.single.shard.ShardSingleIndexLoadService;
import com.hzq.researchtest.service.single.shard.ShardSingleIndexService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/30 17:18
 */
@Service
@Slf4j
public class ShardSingleIndexMergeLoadService extends SingleIndexCommonService {
    /**
     * Description:
     * 搜索入口，并发搜索所有分片
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:33
     */
    public List<String> search(String index, String query) {
        if (!this.checkIndex(index)) {
            log.warn("索引不存在{}", index);
            return null;
        }
        Map<String, FieldDef> fieldMap = indexConfig.getIndexMap().get(index).getFieldMap();
        Map<Integer, Pair<ShardSingleIndexService, ShardSingleIndexLoadService>> indexLoadServiceMap = SHARD_INDEX_MAP.get(index);
        if (indexLoadServiceMap == null) {
            log.warn("索引不存在{}", index);
            return null;
        }
        long start = System.currentTimeMillis();
        try {
            List<Supplier<List<Map<String,String>>>> list = indexLoadServiceMap.values().stream()
                    .map(service -> (Supplier<List<Map<String,String>>>) () -> service.getRight().search(fieldMap,query))
                    .collect(Collectors.toList());

            List<List<Map<String,String>>> collects = AsynUtil.submitToListBySupplier(executorService, list);

            List<Map<String,String>> res = collects.stream().flatMap(Collection::stream)
                    .sorted((o1,o2)->{
                        Double d1 = Double.parseDouble(o1.get("score"));
                        Double d2 = Double.parseDouble(o2.get("score"));
                        return d2.compareTo(d1);
                    })
                    .collect(Collectors.toList());

            log.info("分片查询结束：{}", System.currentTimeMillis() - start);
            return res.stream().map(JSON::toJSONString).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查询失败：{}", e.getMessage(), e);
        }
        return null;
    }
}
