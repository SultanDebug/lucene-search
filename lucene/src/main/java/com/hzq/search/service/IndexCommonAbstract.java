package com.hzq.search.service;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hzq.search.config.FieldDef;
import com.hzq.search.config.IndexConfig;
import com.hzq.search.config.IndexShardConfig;
import com.hzq.search.service.shard.ShardIndexLoadService;
import com.hzq.search.service.shard.ShardIndexService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Huangzq
 * @date 2022/12/5 19:11
 */
@Slf4j
public abstract class IndexCommonAbstract {
    public static final int MAIN_INDEX = 1;
    public static final int ALIA_INDEX = 2;
    static final int NO_EXISTS = 0;
    //EXECUTOR_SERVICE
    public static ThreadPoolExecutor EXECUTOR_SERVICE = new ThreadPoolExecutor(
            100,
            1000,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            t -> {
                Thread tt = new Thread(t);
                tt.setName("shard-index-search-" + tt.getId());
                tt.setUncaughtExceptionHandler((Thread ttt, Throwable e) -> {
                    log.error("[{}]:捕获到异常：", ttt.getName(), e);
                });
                return tt;
            },
            new ThreadPoolExecutor.AbortPolicy() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor pool) {
                    log.error("拒绝策略:: 总线程数：{}， 活动线程数：{}， 排队线程数：{}， 执行完成线程数：{}", pool.getTaskCount(),
                            pool.getActiveCount(), pool.getQueue().size(), pool.getCompletedTaskCount());
                }
            });
    public static Map<String, Map<Integer, Pair<ShardIndexService, ShardIndexLoadService>>> SHARD_INDEX_MAP = new HashMap<>();
    public static Map<String, Integer> CUR_INDEX_MAP = new HashMap<>();
    @Resource
    public IndexConfig indexConfig;

    public static JSONObject getCurrentIndexInfo(String path) {
        File file = new File(path);
        JSONObject res = null;
        if (file.exists()) {
            try (
                    FileInputStream fis = new FileInputStream(file);
                    InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
            ) {
                String dataLine = "";
                StringBuilder buffer = new StringBuilder();
                while ((dataLine = br.readLine()) != null) {
                    buffer.append(dataLine);
                }
                //兼容处理
                if ("1".equals(buffer.toString()) || "2".equals(buffer.toString())) {
                    file.delete();
                    log.info("兼容处理，删除索引记录：{}", buffer);
                    return null;
                }

                res = JSON.parseObject(buffer.toString());
                log.info("当前索引信息：{}", res);
                return res;
            } catch (Exception e) {
                log.error("索引记录文件读取失败:{},{}", res, path);
                throw new RuntimeException("索引记录文件读取失败", e);
            }
        }
        return res;
    }

    public static void main(String[] args) {
        try {
            String switchIndex = "D:\\searchfile\\switch.data";

            File file = new File(switchIndex);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(String.valueOf(1));
                writer.flush();
            } catch (Exception e) {
                log.error("索引记录文件写入失败:{}", switchIndex);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Description:
     * 索引配置信息检查，如果没初始化会预先初始化，在初始化、修改、合并、查询会调起此方法
     *
     * @param isInit 是否初始化
     * @param index  索引名称
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:30
     */
    public boolean checkIndex(boolean isInit, String index) {

        //校验是否配置
        Map<String, IndexShardConfig> indexMap = indexConfig.getIndexMap();
        IndexShardConfig indexShardConfig = indexMap.get(index);
        if (indexShardConfig == null) {
            log.warn("索引初始化失败，索引未配置：{}", index);
            return false;
        }

        //非初始化时是否已初始化
        Map<Integer, Pair<ShardIndexService, ShardIndexLoadService>> indexServiceMap = SHARD_INDEX_MAP.get(index);
        if (!isInit && indexServiceMap != null) {
            return true;
        }


        //初始化调起或未初始化，有配置信息，开始初始化
        String switchIndex = indexShardConfig.getSwitchIndex();
        String aliaPath = indexShardConfig.getAliaPath();
        Integer shardNum = indexShardConfig.getShardNum();
        String fsPath = indexShardConfig.getFsPath();
        String fsPathName = indexShardConfig.getFsPathName();
        Integer recallSize = indexShardConfig.getRecallSize();
        Map<String, FieldDef> fieldMap = indexShardConfig.getFieldMap();

        //缓存当前索引，区分是主索引还是别名索引，来回切换

        //初始化索引地址
        String indexPath = null;
        String searchPath = null;

        Integer currentIndexInfo = MAIN_INDEX;
        JSONObject switchMap = getCurrentIndexInfo(switchIndex);
        if (switchMap != null && switchMap.containsKey(index)) {
            currentIndexInfo = switchMap.getInteger(index);
            searchPath = currentIndexInfo.equals(MAIN_INDEX) ? fsPath : aliaPath;
        } else {
            searchPath = aliaPath;
        }

        if (isInit) {
            CUR_INDEX_MAP.put(index, currentIndexInfo.equals(MAIN_INDEX) ? ALIA_INDEX : MAIN_INDEX);
            indexPath = currentIndexInfo.equals(MAIN_INDEX) ? aliaPath : fsPath;
        } else {
            indexPath = searchPath;
        }

        Map<Integer, Pair<ShardIndexService, ShardIndexLoadService>> map = new HashMap<>();
        for (int i = 0; i < shardNum; i++) {
            ShardIndexLoadService loadService = new ShardIndexLoadService();
            loadService.setShardNum(i, recallSize);
            loadService.setFsPath(searchPath, i, fsPathName);
            ShardIndexService service = new ShardIndexService();
            service.setShardNum(i);
            service.setShardIndexLoadService(loadService);
            service.setFsPath(indexPath, i, fsPathName);
            service.setFieldConfigs(fieldMap);
            map.put(i, Pair.of(service, loadService));
        }
        SHARD_INDEX_MAP.put(index, map);
        return true;
    }

    public boolean saveCurrentIndexInfo(String index, String update) {
        //校验是否配置
        Map<String, IndexShardConfig> indexMap = indexConfig.getIndexMap();
        IndexShardConfig indexShardConfig = indexMap.get(index);
        if (indexShardConfig == null) {
            log.warn("索引初始化失败，索引未配置：{}", index);
            return false;
        }

        String switchIndex = indexShardConfig.getSwitchIndex();

        File file = new File(switchIndex);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(update);
            writer.flush();
            return true;
        } catch (Exception e) {
            log.error("索引记录文件写入失败:{},{}", update, switchIndex);
        }
        return false;
    }
}
