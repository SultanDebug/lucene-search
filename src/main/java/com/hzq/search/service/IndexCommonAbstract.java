package com.hzq.search.service;


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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Huangzq
 * @date 2022/12/5 19:11
 */
@Slf4j
public abstract class IndexCommonAbstract {

    static final int NO_EXISTS = 0;
    static final int MAIN_INDEX = 1;
    static final int ALIA_INDEX = 2;
    public static ExecutorService executorService = new ThreadPoolExecutor(100,
            1000,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
    @Resource
    public IndexConfig indexConfig;

    public static Map<String, Map<Integer, Pair<ShardIndexService, ShardIndexLoadService>>> SHARD_INDEX_MAP = new HashMap<>();
    public static Map<String, Integer> CUR_INDEX_MAP = new HashMap<>();


    /**
     * Description:
     * 索引配置信息检查，如果没初始化会预先初始化，在初始化、修改、合并、查询会调起此方法
     *
     * @param isInit 是否初始化
     * @param index 索引名称
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:30
     */
    public boolean checkIndex(boolean isInit,String index) {

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
        Map<String, FieldDef> fieldMap = indexShardConfig.getFieldMap();

        //缓存当前索引，区分是主索引还是别名索引，来回切换
        Integer currentIndexInfo = CUR_INDEX_MAP.computeIfAbsent(index,s -> getCurrentIndexInfo(switchIndex));
        String mainPath = null;
        if(isInit){
            CUR_INDEX_MAP.put(index,currentIndexInfo.equals(MAIN_INDEX)?ALIA_INDEX:MAIN_INDEX);
            mainPath = currentIndexInfo.equals(MAIN_INDEX) ? aliaPath : fsPath;
        }else{
            mainPath = currentIndexInfo.equals(MAIN_INDEX) ? fsPath : aliaPath;
        }

        Map<Integer, Pair<ShardIndexService, ShardIndexLoadService>> map = new HashMap<>();
        for (int i = 0; i < shardNum; i++) {
            ShardIndexLoadService loadService = new ShardIndexLoadService();
            loadService.setShardNum(i);
            loadService.setFsPath(mainPath, i, fsPathName);
            ShardIndexService service = new ShardIndexService();
            service.setShardNum(i);
            service.setShardIndexLoadService(loadService);
            service.setFsPath(mainPath, i, fsPathName);
            service.setFieldConfigs(fieldMap);
            map.put(i, Pair.of(service, loadService));
        }
        SHARD_INDEX_MAP.put(index, map);
        return true;
    }

    public Integer getCurrentIndexInfo(String path) {
        File file = new File(path);
        int res = NO_EXISTS;
        if (file.exists()) {
            try (
                    FileInputStream fis = new FileInputStream(file);
                    InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
            ) {
                String dataLine = "";
                while ((dataLine = br.readLine()) != null) {
                    res = Integer.parseInt(dataLine);
                }
                log.info("当前索引信息：{}", res);
                return res;
            } catch (Exception e) {
                log.error("索引记录文件读取失败:{},{}", res, path);
                throw new RuntimeException("索引记录文件读取失败",e);
            }
        }
        return res;
    }

    public static void main(String[] args) {
        try {
            String switchIndex = "D:\\searchfile\\switch.data";

            File file = new File(switchIndex);
            try (FileWriter writer = new FileWriter(file)){
                writer.write(String.valueOf(1));
                writer.flush();
            } catch (Exception e) {
                log.error("索引记录文件写入失败:{}",switchIndex);
            }
        }catch (Exception e){}
    }


    public boolean saveCurrentIndexInfo(String index,Integer cur){
        //校验是否配置
        Map<String, IndexShardConfig> indexMap = indexConfig.getIndexMap();
        IndexShardConfig indexShardConfig = indexMap.get(index);
        if (indexShardConfig == null) {
            log.warn("索引初始化失败，索引未配置：{}", index);
            return false;
        }

        String switchIndex = indexShardConfig.getSwitchIndex();

        File file = new File(switchIndex);
        try (FileWriter writer = new FileWriter(file)){
            writer.write(String.valueOf(cur));
            writer.flush();
            return true;
        } catch (Exception e) {
            log.error("索引记录文件写入失败:{},{}",cur,switchIndex);
        }
        return false;
    }
}
