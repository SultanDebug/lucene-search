package com.hzq.researchtest;

import com.alibaba.fastjson.JSON;
import com.bird.segment.extend.IExtendAnalyzer;
import com.hzq.researchtest.service.LuceneService;
import com.hzq.researchtest.service.ResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 19:49
 */
@RestController
@Slf4j
public class TestController {

    @Autowired
    private LuceneService luceneService;

    @GetMapping(value = "/test")
    public ResultResponse<List<String>> test(@RequestParam("field") String field,@RequestParam("query") String query){

        try {
            List<String> name = luceneService.search(field, query);
            return ResultResponse.success(name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
