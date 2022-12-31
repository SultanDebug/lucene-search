package com.hzq.researchtest;

import com.bird.search.common.ResultResponse;
import com.hzq.researchtest.analyzer.MyOnlyPinyinAnalyzer;
import com.hzq.researchtest.service.single.ShardSingleIndexMergeLoadService;
import com.hzq.researchtest.service.single.ShardSingleIndexMergeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.*;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 目前支持：
 * 1.布尔查询
 * 2.字段分分词器
 * 3.拼音分词器
 * 4.前缀查询，短语查询，拼音倒排，简拼查询  youxian公司
 * 5.增量索引，索引合并
 * 6.字段定义，字段查询
 * 问题：
 * 1.宽表数据查询没做适配
 * 2.范围查询
 * 3.相关性计算问题
 * 4.查询需要定制
 *
 * @author Huangzq
 * @description
 * @date 2022/11/17 19:49
 */
@RestController
@Slf4j
@Scope
public class TestShardSingleIndexController {
    @Autowired
    private ShardSingleIndexMergeLoadService shardIndexMergeLoadService;


    @Autowired
    private ShardSingleIndexMergeService shardIndexMergeService;

    @GetMapping(value = "/shard/py-analyzer")
    public ResultResponse<List<String>> pyAnalyzer(@RequestParam("smart") Boolean smart,@RequestParam("query") String query) throws Exception {
        MyOnlyPinyinAnalyzer ikAnalyzer = new MyOnlyPinyinAnalyzer(smart);
        TokenStream tokenStream = ikAnalyzer.tokenStream("hzq", new StringReader(query));
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
        tokenStream.reset();//必须
        List<String> res = new ArrayList<>();
        while (tokenStream.incrementToken()) {
            res.add(termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
        }
        tokenStream.close();//必须
        ikAnalyzer.close();
        return ResultResponse.success(res);
    }
    @GetMapping(value = "/shard/ik-analyzer")
    public ResultResponse<List<String>> ikAnalyzer(@RequestParam("smart") Boolean smart,@RequestParam("query") String query) throws Exception {
        IKAnalyzer ikAnalyzer = new IKAnalyzer(smart);
        TokenStream tokenStream = ikAnalyzer.tokenStream("hzq", new StringReader(query));
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
        tokenStream.reset();//必须
        List<String> res = new ArrayList<>();
        while (tokenStream.incrementToken()) {
            res.add(termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
        }
        tokenStream.close();//必须
        ikAnalyzer.close();
        return ResultResponse.success(res);
    }

    @GetMapping(value = "/shard/query")
    public ResultResponse<Map<String,Object>> query(@RequestParam("index") String index, @RequestParam("query") String query) {

        Map<String,Object> name = shardIndexMergeLoadService.search(index, query);
        return ResultResponse.success(name);
    }


    @GetMapping(value = "/shard/create")
    public ResultResponse<List<String>> create(@RequestParam("index") String index) {
        shardIndexMergeService.initShardIndexForPage(index);
        return ResultResponse.success();
    }

    @PostMapping(value = "/shard/add/{index}")
    public ResultResponse<String> add(@PathVariable("index") String index, @RequestBody Map<String, Object> data) {
        shardIndexMergeService.addIndex(index, data);
        return ResultResponse.success();
    }

    @GetMapping(value = "/shard/merge")
    public ResultResponse<String> merge(@RequestParam("index") String index) {
        shardIndexMergeService.indexMerge(index);
        return ResultResponse.success();
    }
}
