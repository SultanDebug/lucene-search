package com.hzq.researchtest;

import com.hzq.researchtest.service.ResultResponse;
import com.hzq.researchtest.service.index.create.IndexCreateService;
import com.hzq.researchtest.service.index.load.IndexLoadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 19:49
 */
@RestController
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
    public ResultResponse<List<String>> test(@RequestParam("field") String field, @RequestParam("query") String query) {

        try {
            List<String> name = indexLoadService.search(field,query);
            return ResultResponse.success(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @GetMapping(value = "/create")
    public ResultResponse<List<String>> test() {

        try {
            indexCreateService.initIndex();
            return ResultResponse.success();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
