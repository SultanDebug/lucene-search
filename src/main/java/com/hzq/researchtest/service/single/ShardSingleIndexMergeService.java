package com.hzq.researchtest.service.single;

import com.alibaba.druid.pool.DruidDataSource;
import com.bird.search.utils.AsynUtil;
import com.hzq.researchtest.config.FieldDef;
import com.hzq.researchtest.service.single.shard.ShardSingleIndexService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/30 15:28
 */
@Service
@Slf4j
public class ShardSingleIndexMergeService extends SingleIndexCommonService {
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
        if (!this.checkIndex(index)) {
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
        if (!this.checkIndex(index)) {
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
        ShardSingleIndexService shardIndexService = SHARD_INDEX_MAP.get(index).get(shadId.intValue()).getLeft();
        if (shardIndexService == null) {
            log.error("数据分片异常：{}", shadId);
            return;
        }
        shardIndexService.addIndex(fieldMap, idStr, data);
    }

    /**
     * Description:
     * 索引初始化
     * 按分片数哈希分布，并发初始化
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:35
     */
    public void initIndex(String index) {
        if (!this.checkIndex(index)) {
            return;
        }
        Integer shardNum = indexConfig.getIndexMap().get(index).getShardNum();
        Map<String, FieldDef> fieldMap = indexConfig.getIndexMap().get(index).getFieldMap();

        Set<String> fields = fieldMap.values().stream()
                .filter(o -> o.getDbFieldFlag() == 1)
                .map(FieldDef::getFieldName)
                .collect(Collectors.toSet());
        try {
            //获取数据
//            Connection con = getCon();
            String url = "jdbc:mysql://bird-search-db-dev.qizhidao.net:3306/bird_search_db?characterEncoding=utf8&useSSL=false&allowMultiQueries=true";

            JdbcTemplate jdbcTemplate = createJdbcTemplate(url, "bird_search_ro", "0fhfdws9jr3NXS5g5g90");
            List<Map<String, Object>> res = new ArrayList<>();
//            Statement stmt = con.createStatement();

            for (int i = 0; i < 1; i++) {
                List<Map<String, Object>> call = call(fields, jdbcTemplate, i, 10000);
                res.addAll(call);
                log.info("数据加载进度：{}", i);
            }

            /*int size = 10000;
            long maxId = 0;
            long sum = 0;
            while (true){
                List<Map<String, String>> tmps = query(fields, maxId, stmt, size);
                if (tmps.size() < size) {
                    break;
                }
                maxId = Long.parseLong(tmps.get(tmps.size()-1).get("id"));
                res.addAll(tmps);
                sum+=tmps.size();
                log.info("数据加载进度：{}", sum);
            }*/
//            stmt.close();

            //开始初始化
            Map<Long, List<Map<String, Object>>> collect = res.stream()
                    .collect(Collectors.groupingBy(o -> (Long) o.get("id") % shardNum
                            , Collectors.mapping(o1 -> o1, Collectors.toList())));

            List<AsynUtil.TaskExecute> tasks = SHARD_INDEX_MAP.get(index).entrySet().stream()
                    .map(o -> (AsynUtil.TaskExecute) () -> {
                        List<Map<String, Object>> list = collect.get(o.getKey().longValue());
                        if (CollectionUtils.isEmpty(list)) {
                            return;
                        }
                        o.getValue().getLeft().initIndex(fieldMap, list);
                    }).collect(Collectors.toList());

            AsynUtil.executeSync(executorService, tasks);

        } catch (Exception e) {
            log.error("索引初始化调度失败：{}", e.getMessage(), e);
        }
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
    private List<Map<String, String>> query(Set<String> fields, long maxId , Statement stmt, int size) {
        String fieldStr = StringUtils.join(fields, ",");
        String Sql = "select " + fieldStr + " from bird_search_db.ads_qxb_enterprise_search_sort_filter_wide where id > "+maxId +" order by id " +" limit " + size ;

        List<Map<String, String>> lists = new ArrayList<>();
        try {
            ResultSet rs = stmt.executeQuery(Sql);
            while (rs.next()) {
                Map<String, String> map = new HashMap<>();
                for (String field : fields) {
                    String val = rs.getString(field);
                    if (StringUtils.isNotBlank(val)) {
                        map.put(field, val);
                    }
                }
                lists.add(map);
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lists;
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
    private List<Map<String,Object>> call(Set<String> fields, JdbcTemplate jdbcTemplate, int cnt , long size){
        long pageSize = 10000;
        long min = cnt*pageSize;
        long max = min + pageSize;

        List<Map<String,Object>> tmps = new ArrayList<>();
        List<Map<String,Object>> res = Collections.synchronizedList(tmps);

        List<AsynUtil.TaskExecute> tasks = new ArrayList<>();

        long tmpMin = min;
        long tmpMax = tmpMin+size;
        while(true){
            if(tmpMax>=max){
                long finalTmpMin = tmpMin;
                AsynUtil.TaskExecute task = () -> {
                    log.info("数据页进度开始：{}，{}", finalTmpMin,max);
                    List<Map<String, Object>> query = query(fields, jdbcTemplate, finalTmpMin, max);
                    log.info("数据页进度结束：{}，{}，{}",finalTmpMin, max,query.size());
                    res.addAll(query);

                };
                tasks.add(task);
                break;
            }
            long finalTmpMin1 = tmpMin;
            long finalTmpMax = tmpMax;
            AsynUtil.TaskExecute task = () -> {
                log.info("数据页进度开始：{}，{}", finalTmpMin1, finalTmpMax);
                List<Map<String, Object>> query = query(fields, jdbcTemplate, finalTmpMin1, finalTmpMax);
                log.info("数据页进度结束：{}，{}，{}",finalTmpMin1, finalTmpMax,query.size());
                res.addAll(query);
            };
            tasks.add(task);

            tmpMin = tmpMax;
            tmpMax += size;
        }

        try {
            AsynUtil.executeSync(executorService,tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    private List<Map<String, Object>> query(Set<String> fields, JdbcTemplate jdbcTemplate, long min , long max) {
        String fieldStr = StringUtils.join(fields, ",");
        String sql = "select " + fieldStr + " from bird_search_db.ads_qxb_enterprise_search_sort_filter_wide where id > "+min +" and id<= "+max ;
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
     * @date 2022/12/6 19:36
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
