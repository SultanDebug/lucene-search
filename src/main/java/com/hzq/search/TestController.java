package com.hzq.search;

import com.hzq.search.service.ResultResponse;
import com.hzq.search.service.index.create.IndexCreateService;
import com.hzq.search.service.index.load.IndexLoadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 1.布尔查询
 * 2.字段分分词器
 * 3.拼音分词器
 * 4.前缀查询，短语查询，拼音倒排，简拼查询  youxian公司
 * 5.增量索引，索引合并
 * @author Huangzq
 * @description
 * @date 2022/11/17 19:49
 */
//@RestController
@Slf4j
public class TestController {

    /*@Autowired
    private LuceneService luceneService;*/

    /*@Autowired
    private LuceneMultiFieldService luceneMultiFieldService;*/

    @Autowired
    private IndexLoadService indexLoadService;


    @Autowired
    private IndexCreateService indexCreateService;

    @GetMapping(value = "/query")
    public ResultResponse<List<String>> query(@RequestParam("updateFlag") Boolean updateFlag,@RequestParam("field") String field, @RequestParam("query") String query) {

        try {
            List<String> name = indexLoadService.search(updateFlag,field,query);
            return ResultResponse.success(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @GetMapping(value = "/create")
    public ResultResponse<List<String>> create() {

        try {
            indexCreateService.initIndex();
            return ResultResponse.success();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "/add")
    public ResultResponse<String> add(@RequestParam("data") String data) {

        try {
            indexCreateService.addIndex(data);
            return ResultResponse.success();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
