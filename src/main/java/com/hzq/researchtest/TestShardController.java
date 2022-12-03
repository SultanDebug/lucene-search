package com.hzq.researchtest;

import com.hzq.researchtest.service.ResultResponse;
import com.hzq.researchtest.service.shardindex.ShardIndexMergeLoadService;
import com.hzq.researchtest.service.shardindex.ShardIndexMergeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 1.布尔查询
 * 2.字段分分词器
 * 3.拼音分词器
 * 4.前缀查询，短语查询，拼音倒排，简拼查询  youxian公司
 * 5.增量索引，索引合并
 *
 * @author Huangzq
 * @description
 * @date 2022/11/17 19:49
 */
@RestController
@Slf4j
public class TestShardController {
    @Autowired
    private ShardIndexMergeLoadService shardIndexMergeLoadService;


    @Autowired
    private ShardIndexMergeService shardIndexMergeService;

    @GetMapping(value = "/shard/query")
    public ResultResponse<Map<String, List<String>>> query(@RequestParam("query") String query) {

        Map<String, List<String>> name = shardIndexMergeLoadService.search(query);
        return ResultResponse.success(name);
    }


    @GetMapping(value = "/shard/create")
    public ResultResponse<List<String>> create() {
        shardIndexMergeService.initIndex();
        return ResultResponse.success();
    }

    @GetMapping(value = "/shard/add")
    public ResultResponse<String> add(@RequestParam("id") Long id, @RequestParam("data") String data) {
        shardIndexMergeService.addIndex(id, data);
        return ResultResponse.success();
    }

    @GetMapping(value = "/shard/merge")
    public ResultResponse<String> merge() {
        shardIndexMergeService.indexMerge();
        return ResultResponse.success();
    }
}
