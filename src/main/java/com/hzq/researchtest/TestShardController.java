package com.hzq.researchtest;

import com.hzq.researchtest.config.IndexConfig;
import com.hzq.researchtest.service.ResultResponse;
import com.hzq.researchtest.service.shardindex.ShardIndexMergeLoadService;
import com.hzq.researchtest.service.shardindex.ShardIndexMergeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 目前支持：
 *      1.布尔查询
 *      2.字段分分词器
 *      3.拼音分词器
 *      4.前缀查询，短语查询，拼音倒排，简拼查询  youxian公司
 *      5.增量索引，索引合并
 *      6.字段定义，字段查询
 * 问题：
 *      1.宽表数据查询没做适配
 *      2.范围查询
 *      3.相关性计算问题
 *      4.查询需要定制
 *
 * @author Huangzq
 * @description
 * @date 2022/11/17 19:49
 */
//@RestController
@Slf4j
//@Scope
public class TestShardController {
    //@Autowired
    private ShardIndexMergeLoadService shardIndexMergeLoadService;


    //@Autowired
    private ShardIndexMergeService shardIndexMergeService;

    @GetMapping(value = "/shard/query")
    public ResultResponse<Map<String, List<String>>> query(@RequestParam("index") String index , @RequestParam("query") String query) {

        Map<String, List<String>> name = shardIndexMergeLoadService.search(index,query);
        return ResultResponse.success(name);
    }


    @GetMapping(value = "/shard/create")
    public ResultResponse<List<String>> create(@RequestParam("index") String index ) {
        shardIndexMergeService.initIndex(index);
        return ResultResponse.success();
    }

    @PostMapping(value = "/shard/add/{index}")
    public ResultResponse<String> add(@PathVariable("index") String index , @RequestBody Map<String,String> data) {
        shardIndexMergeService.addIndex(index, data);
        return ResultResponse.success();
    }

    @GetMapping(value = "/shard/merge")
    public ResultResponse<String> merge(@RequestParam("index") String index ) {
        shardIndexMergeService.indexMerge(index);
        return ResultResponse.success();
    }
}
