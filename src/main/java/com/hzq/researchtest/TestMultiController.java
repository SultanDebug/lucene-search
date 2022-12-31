package com.hzq.researchtest;

import com.hzq.researchtest.service.ResultResponse;
import com.hzq.researchtest.service.multiindex.MultiIndexLoadService;
import com.hzq.researchtest.service.multiindex.MultiIndexService;
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
//@RestController
@Slf4j
public class TestMultiController {
    @Autowired
    private MultiIndexLoadService indexLoadService;


    @Autowired
    private MultiIndexService indexService;

    @GetMapping(value = "/mult/query")
    public ResultResponse<Map<String, List<String>>> query(@RequestParam("query") String query) {

        Map<String, List<String>> name = indexLoadService.search(query);
        return ResultResponse.success(name);
    }


    @GetMapping(value = "/mult/create")
    public ResultResponse<List<String>> create() {
        indexService.initIndex();
        return ResultResponse.success();
    }

    @GetMapping(value = "/mult/add")
    public ResultResponse<String> add(@RequestParam("id") Long id, @RequestParam("data") String data) {
        indexService.addIndex(id, data);
        return ResultResponse.success();
    }

    @GetMapping(value = "/mult/merge")
    public ResultResponse<String> merge() {
        indexService.indexMerge();
        return ResultResponse.success();
    }
}
