package com.hzq.search.service.shardindex;

import com.hzq.search.config.FieldDef;
import com.hzq.search.service.shardindex.shard.ShardIndexService;
import com.hzq.search.util.AsynUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
//@Service
@Slf4j
public class ShardIndexMergeService extends IndexCommonService {
    /**
     * Description:
     *  索引合并，并发所有分片合并主、增量索引，并通知所有搜索实例更新索引信息
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
     *  索引数据新增
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:35
     */
    public void addIndex(String index, Map<String, String> data) {
        if (!this.checkIndex(index)) {
            return;
        }

        String idStr = data.get("id");

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
     *  索引初始化
     *      按分片数哈希分布，并发初始化
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
            Connection con = getCon();
            List<Map<String, String>> res = new ArrayList<>();
            Statement stmt = con.createStatement();
            int size = 10000;
            for (int i = 0; i < 1; i++) {
                List<Map<String, String>> tmps = query(fields, i, stmt, size);
                if (tmps.size() < size) {
                    break;
                }
                res.addAll(tmps);
            }
            stmt.close();
            con.close();

            //开始初始化
            Map<Long, List<Map<String, String>>> collect = res.stream()
                    .collect(Collectors.groupingBy(o -> Long.parseLong(o.get("id")) % shardNum
                            , Collectors.mapping(o1 -> o1, Collectors.toList())));

            List<AsynUtil.TaskExecute> tasks = SHARD_INDEX_MAP.get(index).entrySet().stream()
                    .map(o -> (AsynUtil.TaskExecute) () -> {
                        List<Map<String, String>> list = collect.get(o.getKey().longValue());
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
     *  宽表数据加载
     *  问题：未作适配
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:36
     */
    private List<Map<String, String>> query(Set<String> fields, int offset, Statement stmt, int size) {
        String fieldStr = StringUtils.join(fields, ",");
        int start = offset * size;
        String Sql = "select " + fieldStr + " from db.table limit " + start + " , " + size;

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
     *  宽表数据库连接
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:36
     */
    private Connection getCon() {
        Connection conn = null;
        String driver = "com.mysql.cj.jdbc.Driver";
        String url = "jdbc:mysql://host:3306/db?characterEncoding=utf8&useSSL=false&allowMultiQueries=true";
        try {
            //注册（加载）驱动程序
            Class.forName(driver);
            //获取数据库接
            conn = DriverManager.getConnection(url, "username", "pass");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return conn;
    }


}
