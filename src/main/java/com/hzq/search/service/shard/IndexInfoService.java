package com.hzq.search.service.shard;

import com.alibaba.fastjson.JSONObject;
import com.hzq.search.config.IndexConfig;
import com.hzq.search.config.IndexShardConfig;
import com.hzq.search.service.IndexCommonAbstract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author Huangzq
 * @description
 * @date 2023/3/14 11:00
 */
@Slf4j
@Service
public class IndexInfoService extends IndexCommonAbstract {
    @Resource
    public IndexConfig indexConfig;

    public void getCurIndex(String index, Map<String,String> map){
        Map<String, IndexShardConfig> indexMap = indexConfig.getIndexMap();
        String config = "index";
        if (indexMap == null) {
            log.warn("索引未配置：{}", index);
            map.put(config,"索引未配置") ;
            return;
        }
        //
        IndexShardConfig indexShardConfig = indexMap.get(index);
        if (indexShardConfig == null) {
            log.warn("索引参数未配置：{}", index);
            map.put(config,"索引参数未配置") ;
            return;
        }
        //持久化信息
        JSONObject currentIndexInfo = getCurrentIndexInfo(indexShardConfig.getSwitchIndex());
        String switchIndex = "switchIndex";
        if(currentIndexInfo==null){
            map.put(switchIndex,"当前使用别名索引") ;
        }else {
            if(currentIndexInfo.containsKey(index)){
                Integer indexStatus = currentIndexInfo.getInteger(index);
                if(indexStatus.equals(MAIN_INDEX)){
                    map.put(switchIndex,"当前持久化使用主索引") ;
                }else{
                    map.put(switchIndex,"当前持久化使用别名索引") ;
                }
            }else {
                map.put(switchIndex,"索引未初始化") ;
            }
        }

        //内存信息
        Integer ramStatus = CUR_INDEX_MAP.get(index);
        String ramIndex = "ramIndex";
        if(ramStatus==null){
            map.put(ramIndex,"当前索引内存信息不存在") ;
        }else {
            if(ramStatus.equals(MAIN_INDEX)){
                map.put(ramIndex,"当前内存使用主索引") ;
            }else{
                map.put(ramIndex,"当前内存使用别名索引") ;
            }
        }

    }
}
