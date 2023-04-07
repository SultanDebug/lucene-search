package com.hzq.search.controller;

import com.hzq.common.dto.ResultResponse;
import com.hzq.search.service.EsService;
import com.hzq.search.service.plugin.PluginService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Huangzq
 * @description
 * @date 2023/4/3 09:47
 */
@RestController
public class EsController {
    @Autowired
    private EsService esService;

    @Autowired
    private PluginService pluginService;

    @GetMapping("/es/query")
    public ResultResponse<List<Map<String, Object> >> query(@RequestParam("query") String query) {
        SearchResponse query1 = esService.query();
        List<Map<String, Object> > list = new ArrayList<>();
        for (SearchHit hit : query1.getHits()) {
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            list.add(sourceAsMap);
        }

        return ResultResponse.success(list);
    }


    @GetMapping("/es/plugin")
    public ResultResponse<String> plugin(@RequestParam("query") String query) throws Exception {
        return ResultResponse.success(pluginService.load());
    }
}
