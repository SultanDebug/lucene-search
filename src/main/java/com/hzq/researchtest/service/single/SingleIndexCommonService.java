package com.hzq.researchtest.service.single;

import com.hzq.researchtest.config.FieldDef;
import com.hzq.researchtest.config.IndexConfig;
import com.hzq.researchtest.config.IndexShardConfig;
import com.hzq.researchtest.service.single.shard.ShardSingleIndexLoadService;
import com.hzq.researchtest.service.single.shard.ShardSingleIndexService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/5 19:11
 */
@Slf4j
public abstract class SingleIndexCommonService {
    public static ExecutorService executorService = new ThreadPoolExecutor(50,
            1000,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
    @Resource
    public IndexConfig indexConfig;

    public static Map<String, Map<Integer, Pair<ShardSingleIndexService, ShardSingleIndexLoadService>>> SHARD_INDEX_MAP = new HashMap<>();


    /**
     * Description:
     * 索引配置信息检查，如果没初始化会预先初始化，在初始化、修改、合并、查询会调起此方法
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:30
     */
    public boolean checkIndex(String index) {

        //校验是否配置
        Map<String, IndexShardConfig> indexMap = indexConfig.getIndexMap();
        IndexShardConfig indexShardConfig = indexMap.get(index);
        if (indexShardConfig == null) {
            log.warn("索引初始化失败，索引未配置：{}", index);
            return false;
        }

        //已初始化
        Map<Integer, Pair<ShardSingleIndexService, ShardSingleIndexLoadService>> indexServiceMap = SHARD_INDEX_MAP.get(index);
        if (indexServiceMap != null) {
            return true;
        }


        //未初始化，有配置信息，开始初始化
        Integer shardNum = indexShardConfig.getShardNum();
        String fsPath = indexShardConfig.getFsPath();
        String fsPathName = indexShardConfig.getFsPathName();
        Map<String, FieldDef> fieldMap = indexShardConfig.getFieldMap();

        Map<Integer, Pair<ShardSingleIndexService, ShardSingleIndexLoadService>> map = new HashMap<>();
        for (int i = 0; i < shardNum; i++) {
            ShardSingleIndexLoadService loadService = new ShardSingleIndexLoadService();
            loadService.setShardNum(i);
            loadService.setFsPath(fsPath, i, fsPathName);
            ShardSingleIndexService service = new ShardSingleIndexService();
            service.setShardNum(i);
            service.setShardIndexLoadService(loadService);
            service.setFsPath(fsPath, i, fsPathName);
            service.setFieldConfigs(fieldMap);
            map.put(i, Pair.of(service, loadService));
        }
        SHARD_INDEX_MAP.put(index, map);
        return true;
    }
}
