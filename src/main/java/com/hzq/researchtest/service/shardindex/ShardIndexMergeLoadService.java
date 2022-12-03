package com.hzq.researchtest.service.shardindex;

import com.bird.search.utils.AsynUtil;
import com.hzq.researchtest.config.ShardConfig;
import com.hzq.researchtest.service.shardindex.shard.ShardIndexLoadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/30 17:18
 */
@Service
@Slf4j
public class ShardIndexMergeLoadService {
    private static Map<Integer, ShardIndexLoadService> SHARD_LOAD_MAP = new HashMap<>();

    private static ExecutorService executorService = Executors.newFixedThreadPool(ShardConfig.SHARD_NUM);

    public static void initLoadMap(Integer shardId, ShardIndexLoadService service){
        SHARD_LOAD_MAP.put(shardId,service);
    }

    public Map<String,List<String>> search(String query){
        long start = System.currentTimeMillis();
        try {
            List<String> fsList = new ArrayList<>();
            List<String> incrList = new ArrayList<>();

            List<Supplier<Map<String, List<String>>>> list = SHARD_LOAD_MAP.values().stream()
                    .map(service -> (Supplier<Map<String, List<String>>>) () -> service.search(query))
                    .collect(Collectors.toList());

            List<Map<String, List<String>>> collects = AsynUtil.submitToListBySupplier(executorService, list);
            for (Map<String, List<String>> collect : collects) {
                List<String> fss = collect.get("fs");
                List<String> incrs = collect.get("incr");
                if(!CollectionUtils.isEmpty(fss)){
                    fsList.addAll(fss);
                }

                if(!CollectionUtils.isEmpty(incrs)){
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

            Map<String,List<String>> map = new HashMap<>();
            map.put("fs",fsList);
            map.put("incr",incrList);
            log.info("分片查询结束：{}",System.currentTimeMillis()-start);
            return map;
        }catch (Exception e){
            log.error("查询失败：{}",e.getMessage(),e);
        }
        return new HashMap<>();
    }
}
