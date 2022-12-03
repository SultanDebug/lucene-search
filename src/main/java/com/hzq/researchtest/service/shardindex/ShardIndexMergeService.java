package com.hzq.researchtest.service.shardindex;

import com.bird.search.utils.AsynUtil;
import com.hzq.researchtest.config.ShardConfig;
import com.hzq.researchtest.service.shardindex.shard.ShardIndexLoadService;
import com.hzq.researchtest.service.shardindex.shard.ShardIndexService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.hzq.researchtest.config.ShardConfig.*;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/30 15:28
 */
@Service
@Slf4j
public class ShardIndexMergeService implements InitializingBean {
    private static Map<Integer, ShardIndexService> SHARD_INDEX_MAP = new HashMap<>();
    private static ExecutorService executorService = Executors.newFixedThreadPool(ShardConfig.SHARD_NUM);
    public void indexMerge() {
        long start = System.currentTimeMillis();
        AtomicLong res = new AtomicLong(0);

        List<AsynUtil.TaskExecute> tasks = SHARD_INDEX_MAP.values().stream()
                .map(shardIndexService -> (AsynUtil.TaskExecute) () -> {
                    res.addAndGet(shardIndexService.indexMerge());
                }).collect(Collectors.toList());

        try {
            AsynUtil.executeSync(executorService,tasks);
        } catch (Exception e) {
            log.error("数据合并异常：{}",e.getMessage(),e);
        }

        /*for (Map.Entry<Integer, ShardIndexService> entry : SHARD_INDEX_MAP.entrySet()) {
            Long aLong = entry.getValue().indexMerge();
            if(aLong!=null){
                res.addAndGet(aLong);
            }
        }*/
        log.info("合并数量：{},花费：{}",res,System.currentTimeMillis()-start);
    }

    public void addIndex(Long id , String data) {
        Long shadId = id % SHARD_NUM;
        ShardIndexService shardIndexService = SHARD_INDEX_MAP.get(shadId.intValue());
        if(shardIndexService == null){
            log.error("数据分片异常：{}",shadId);
            return;
        }
        shardIndexService.addIndex(id,data);
    }

    public void initIndex() {
        try {
            //获取数据
            Connection con = getCon();
            List<Pair<Long,String>> res = new ArrayList<>();
            Statement stmt = con.createStatement();
            int size = 10000;
            for (int i = 0; i < 1; i++) {
                List<Pair<Long,String>> tmps = query(i, stmt, size);
                if (tmps.size() < size) {
                    break;
                }
                res.addAll(tmps);
            }
            stmt.close();
            con.close();

            //开始初始化
            Map<Long, List<String>> collect = res.stream().collect(Collectors.groupingBy(o -> o.getLeft() % SHARD_NUM
                    , Collectors.mapping(Pair::getRight, Collectors.toList())));

            List<AsynUtil.TaskExecute> tasks = SHARD_INDEX_MAP.entrySet().stream()
                    .map(o -> (AsynUtil.TaskExecute) () -> {
                        List<String> list = collect.get(o.getKey().longValue());
                        if (CollectionUtils.isEmpty(list)) {
                            return;
                        }
                        o.getValue().initIndex(list);
                    }).collect(Collectors.toList());

            AsynUtil.executeSync(executorService,tasks);

            /*for (Map.Entry<Integer, ShardIndexService> entry : SHARD_INDEX_MAP.entrySet()) {
                List<String> list = collect.get(entry.getKey().longValue());
                if(CollectionUtils.isEmpty(list)){
                    continue;
                }
                entry.getValue().initIndex(list);
            }*/

        }catch (Exception e){
            log.error("索引初始化调度失败：{}",e.getMessage(),e);
        }
    }


    private List<Pair<Long,String>> query(int offset, Statement stmt, int size) {
        int start = offset * size;
        String Sql = "select id,name from bird_search_db.ads_qxb_enterprise_search_sort_filter_wide limit " + start + " , " + size;

        List<Pair<Long,String>> lists = new ArrayList<>();
        try {
            ResultSet rs = stmt.executeQuery(Sql);
            while (rs.next()) {
                lists.add(Pair.of(rs.getLong("id"),rs.getString("name").substring(1)));
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lists;
    }

    private Connection getCon() {
        Connection conn = null;
        String driver = "com.mysql.cj.jdbc.Driver";
        String url = "jdbc:mysql://bird-search-db-test.qizhidao.net:3306/bird_search_db?characterEncoding=utf8&useSSL=false&allowMultiQueries=true";
        try {
            //注册（加载）驱动程序
            Class.forName(driver);
            //获取数据库接
            conn = DriverManager.getConnection(url, "bird_search_ro", "NTN8Mw2mGGsgs7IDUBea");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return conn;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        for (int i = 0; i < SHARD_NUM; i++) {
            ShardIndexLoadService loadService = new ShardIndexLoadService();
            loadService.setShardNum(i);
            loadService.setFsPath(FS_PATH,i,FS_PATH_NAME);
            loadService.setIncrPath(INCR_PATH,i,INCR_PATH_NAME);
            ShardIndexService service = new ShardIndexService();
            service.setShardNum(i);
            service.setShardIndexLoadService(loadService);
            service.setFsPath(FS_PATH,i,FS_PATH_NAME);
            service.setIncrPath(INCR_PATH,i,INCR_PATH_NAME);
            SHARD_INDEX_MAP.put(i,service);
            ShardIndexMergeLoadService.initLoadMap(i,loadService);
        }
    }
}
