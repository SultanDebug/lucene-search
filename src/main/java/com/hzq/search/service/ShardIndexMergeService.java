package com.hzq.search.service;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.hzq.search.config.FieldDef;
import com.hzq.search.config.IndexShardConfig;
import com.hzq.search.service.shard.ShardIndexLoadService;
import com.hzq.search.service.shard.ShardIndexService;
import com.hzq.search.util.AsynUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author Huangzq
 * @date 2022/11/30 15:28
 */
@Service
@Slf4j
public class ShardIndexMergeService extends IndexCommonAbstract {
    /**
     * Description:
     * 索引合并，并发所有分片合并主、增量索引，并通知所有搜索实例更新索引信息
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:33
     */
    public void indexMerge(String index) {
        if (!this.checkIndex(false, index)) {
            return;
        }
        long start = System.currentTimeMillis();
        AtomicLong res = new AtomicLong(0);

        List<AsynUtil.TaskExecute> tasks = SHARD_INDEX_MAP.get(index).values().stream()
                .map(shardIndexService -> (AsynUtil.TaskExecute) () -> {
                    res.addAndGet(shardIndexService.getLeft().indexMerge());
                }).collect(Collectors.toList());

        try {
            AsynUtil.executeSync(executorService, tasks);
        } catch (Exception e) {
            log.error("数据合并异常：{}", e.getMessage(), e);
        }
        log.info("合并数量：{},花费：{}", res, System.currentTimeMillis() - start);
    }

    /**
     * Description:
     * 索引数据新增
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:35
     */
    public void addIndex(String index, Map<String, Object> data) {
        if (!this.checkIndex(false, index)) {
            return;
        }

        String idStr = data.get("id").toString();

        if (StringUtils.isBlank(idStr)) {
            return;
        }
        long id = Long.parseLong(idStr);

        Integer shardNum = indexConfig.getIndexMap().get(index).getShardNum();
        Map<String, FieldDef> fieldMap = indexConfig.getIndexMap().get(index).getFieldMap();

        Long shadId = id % shardNum;
        ShardIndexService shardIndexService = SHARD_INDEX_MAP.get(index).get(shadId.intValue()).getLeft();
        if (shardIndexService == null) {
            log.error("数据分片异常：{}", shadId);
            return;
        }
        shardIndexService.addIndex(fieldMap, idStr, data);
    }

    /**
     * Description:
     * 按页加载及索引生成
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/14 17:59
     */
    public List<String> initShardIndexForPage(String index) {
        //实例参数检查以及初始化
        if (!this.checkIndex(true, index)) {
            return null;
        }
        //配置获取
        Integer shardNum = indexConfig.getIndexMap().get(index).getShardNum();
        Map<String, FieldDef> fieldMap = indexConfig.getIndexMap().get(index).getFieldMap();

        Set<String> fields = fieldMap.values().stream()
                .filter(o -> o.getDbFieldFlag() == 1)
                .map(FieldDef::getFieldName)
                .collect(Collectors.toSet());

        IndexShardConfig indexShardConfig = indexConfig.getIndexMap().get(index);
        try {
            //数据源实例
            String url = indexShardConfig.getDbUrl();
            JdbcTemplate jdbcTemplate = createJdbcTemplate(url, indexShardConfig.getDbUserName(), indexShardConfig.getDbPass());

            List<String> errors = new ArrayList<>();
            List<String> errorSqls = Collections.synchronizedList(errors);

            //清理旧索引数据
            for (Map.Entry<Integer, Pair<ShardIndexService, ShardIndexLoadService>> entry : SHARD_INDEX_MAP.get(index).entrySet()) {
                entry.getValue().getLeft().deleteAll();
            }

            //todo 测试只去100000
            int pageSize = 100000;
            long maxId = 0;
            long count = 0;
            while (true) {
                //加载数据
                List<Map<String, Object>> tmps = queryForPage(errorSqls, jdbcTemplate, indexShardConfig.getDbTableName(), fields, maxId, pageSize);
                if (CollectionUtils.isEmpty(tmps)) {
                    return Lists.newArrayList("数据库加载异常");
                }
                maxId = (long) tmps.get(tmps.size() - 1).get("id");
                count += tmps.size();

                //开始初始化，哈希取余分片
                Map<Long, List<Map<String, Object>>> collect = tmps.stream()
                        .collect(Collectors.groupingBy(o -> (Long) o.get("id") % shardNum
                                , Collectors.mapping(o1 -> o1, Collectors.toList())));

                //分片索引数据任务生成
                List<AsynUtil.TaskExecute> tasks = SHARD_INDEX_MAP.get(index).entrySet().stream()
                        .map(o -> (AsynUtil.TaskExecute) () -> {
                            List<Map<String, Object>> list = collect.get(o.getKey().longValue());
                            if (CollectionUtils.isEmpty(list)) {
                                return;
                            }
                            o.getValue().getLeft().commitAll(fieldMap, list);
                        }).collect(Collectors.toList());

                //分片任务执行
                AsynUtil.executeSync(executorService, tasks);

                //分片索引加载完成，通知索引切换
                for (Map.Entry<Integer, Pair<ShardIndexService, ShardIndexLoadService>> entry : SHARD_INDEX_MAP.get(index).entrySet()) {
                    entry.getValue().getLeft().flushIndex();
                }

                if (tmps.size() < pageSize) {
                    break;
                }
                // System.gc();
                log.info("总数据加载进度：{}", count);
                //todo 测试代码
                if (count >= pageSize) {
                    break;
                }
            }


            //分片索引加载完成，通知索引切换
            /*for (Map.Entry<Integer, Pair<ShardIndexService, ShardIndexLoadService>> entry : SHARD_INDEX_MAP.get(index).entrySet()) {
                entry.getValue().getLeft().noticeSearcher();
            }*/
            long start = System.currentTimeMillis();
            log.info("段合并开始{}", shardNum);
            Integer curIndex = CUR_INDEX_MAP.get(index);
            String searchPath = curIndex.equals(MAIN_INDEX) ? indexShardConfig.getFsPath() : indexShardConfig.getAliaPath();
            List<AsynUtil.TaskExecute> collect = SHARD_INDEX_MAP.get(index).values().stream()
                    .map(o -> (AsynUtil.TaskExecute) () -> o.getLeft().noticeSearcher(searchPath))
                    .collect(Collectors.toList());
            log.info("段合并结束【{}】", System.currentTimeMillis() - start);

            AsynUtil.executeSync(executorService, collect);

            //保存当前索引信息
            this.saveCurrentIndexInfo(index, JSON.toJSONString(CUR_INDEX_MAP));

            log.warn("失败sql：{}", JSON.toJSONString(errorSqls));

            return errorSqls;


        } catch (Exception e) {
            log.error("索引初始化调度失败：{}", e.getMessage(), e);
        }
        return null;
    }


    /**
     * Description:
     * 宽表数据加载
     * 问题：未作适配
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:36
     */
    private List<Map<String, Object>> query(List<String> errorSqls,
                                            JdbcTemplate jdbcTemplate,
                                            String tableName,
                                            Set<String> fields,
                                            long maxId,
                                            int size) {
        String fieldStr = StringUtils.join(fields, ",");
        String sql = "select " + fieldStr + " from " + tableName + " where id > " + maxId + " order by id " + " limit " + size;

        try {
            List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
            log.info("数据页进度结束：{},{}", maxId, CollectionUtils.isEmpty(maps) ? 0 : maps.size());
            return maps;
        } catch (Exception e) {
            try {
                log.info("异常补偿开始：{}", maxId);
                List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
                log.info("异常补偿结束：{},{}", maxId, CollectionUtils.isEmpty(maps) ? 0 : maps.size());
                return maps;
            } catch (Exception e1) {
                errorSqls.add(sql);
            }
        }
        return null;
    }

    /**
     * 分批加载
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/1/13 10:44
     */
    private List<Map<String, Object>> queryForPage(List<String> errorSqls,
                                                   JdbcTemplate jdbcTemplate,
                                                   String tableName,
                                                   Set<String> fields,
                                                   long maxId,
                                                   int pageSize) {
        List<Map<String, Object>> res = new ArrayList<>();
        int size = 100000;
        while (true) {
            List<Map<String, Object>> tmps = query(errorSqls, jdbcTemplate, tableName, fields, maxId, size);
            if (CollectionUtils.isEmpty(tmps)) {
                break;
            }
            res.addAll(tmps);
            if (tmps.size() < size) {
                break;
            }
            maxId = (long) tmps.get(tmps.size() - 1).get("id");
            if (res.size() >= pageSize) {
                break;
            }
        }

        return res;

    }

    /**
     * Description:
     * 多线程加载
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/14 14:57
     */
    private List<Map<String, Object>> call(List<String> errorSqls, Set<String> fields, JdbcTemplate jdbcTemplate, int cnt, long size) {
        long pageSize = 10000000;
        long min = cnt * pageSize;
        long max = min + pageSize;

        List<Map<String, Object>> tmps = new ArrayList<>();
        List<Map<String, Object>> res = Collections.synchronizedList(tmps);

        List<AsynUtil.TaskExecute> tasks = new ArrayList<>();

        long tmpMin = min;
        long tmpMax = tmpMin + size;
        while (true) {
            if (tmpMax >= max) {
                long finalTmpMin = tmpMin;
                AsynUtil.TaskExecute task = () -> {
                    try {
                        log.info("数据页进度开始：{}，{}", finalTmpMin, max);
                        List<Map<String, Object>> query = query(fields, jdbcTemplate, finalTmpMin, max);
                        log.info("数据页进度结束：{}，{}，{}", finalTmpMin, max, query.size());
                        res.addAll(query);
                    } catch (Exception e) {
                        try {
                            log.info("异常补偿开始：{}，{}", finalTmpMin, max);
                            List<Map<String, Object>> query = query(fields, jdbcTemplate, finalTmpMin, max);
                            log.info("异常补偿结束：{}，{}，{}", finalTmpMin, max, query.size());
                            res.addAll(query);
                        } catch (Exception e1) {
                            errorSqls.add(finalTmpMin + "/" + max);
                        }
                    }
                };
                tasks.add(task);
                break;
            }
            long finalTmpMin1 = tmpMin;
            long finalTmpMax = tmpMax;
            AsynUtil.TaskExecute task = () -> {
                try {
                    log.info("数据页进度开始：{}，{}", finalTmpMin1, finalTmpMax);
                    List<Map<String, Object>> query = query(fields, jdbcTemplate, finalTmpMin1, finalTmpMax);
                    log.info("数据页进度结束：{}，{}，{}", finalTmpMin1, finalTmpMax, query.size());
                    res.addAll(query);
                } catch (Exception e) {
                    try {
                        log.info("异常补偿开始：{}，{}", finalTmpMin1, finalTmpMax);
                        List<Map<String, Object>> query = query(fields, jdbcTemplate, finalTmpMin1, finalTmpMax);
                        log.info("异常补偿结束：{}，{}，{}", finalTmpMin1, finalTmpMax, query.size());
                        res.addAll(query);
                    } catch (Exception e1) {
                        errorSqls.add(finalTmpMin1 + "/" + finalTmpMax);
                    }

                }
            };
            tasks.add(task);

            tmpMin = tmpMax;
            tmpMax += size;
        }

        try {
            AsynUtil.executeSync(executorService, tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    private List<Map<String, Object>> query(Set<String> fields, JdbcTemplate jdbcTemplate, long min, long max) {
        String fieldStr = StringUtils.join(fields, ",");
        String sql = "select " + fieldStr + " from tablename where id > " + min + " and id<= " + max;
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
        return maps;
    }

    /**
     * Description:
     * 宽表数据库连接
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/14 14:56
     */
    public static JdbcTemplate createJdbcTemplate(String url, String username, String password) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setSocketTimeout(-1);
        dataSource.setConnectTimeout(-1);
        return new JdbcTemplate(dataSource);
    }
}
