package com.hzq.search.controller;

import com.hzq.search.analyzer.MyCnPinyinAnalyzer;
import com.hzq.search.service.ResultResponse;
import com.hzq.search.service.ShardIndexMergeLoadService;
import com.hzq.search.service.ShardIndexMergeService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/**
 * 目前支持：
 * 1.布尔查询
 * 2.字段分分词器
 * 3.拼音分词器
 * 4.前缀查询，短语查询，拼音倒排，简拼查询  youxian公司
 * 5.增量索引，索引合并
 * 6.字段定义，字段查询
 * 问题：
 * 1.表数据查询没做适配
 * 2.范围查询
 * 3.相关性计算问题
 * 4.查询需要定制
 *
 * @author Huangzq
 * @date 2022/11/17 19:49
 */
@RestController
@Slf4j
@Scope
public class ShardController {
    @Autowired
    private ShardIndexMergeLoadService shardIndexMergeLoadService;


    @Autowired
    private ShardIndexMergeService shardIndexMergeService;

    @GetMapping(value = "/shard/py-analyzer")
    public ResultResponse<List<String>> pyAnalyzer(@RequestParam("smart") Boolean smart, @RequestParam("query") String query) throws Exception {
        MyCnPinyinAnalyzer ikAnalyzer = new MyCnPinyinAnalyzer(smart);
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
    public ResultResponse<List<String>> ikAnalyzer(@RequestParam("smart") Boolean smart, @RequestParam("query") String query) throws Exception {
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

    /**
     * 查询接口
     *
     * @param index   索引名称
     * @param query   搜索词
     * @param filter  过滤条件，json串  格式详见文档
     * @param size    页大小 默认20
     * @param page    页数 默认0：第一页
     * @param explain 解释，默认不解释  true-是   false-否
     * @param type    查询方式，默认模糊查询：detail-companyid查询  prefix-精确查询，前缀、term  fuzzy-模糊查询  complex-复合查询，拼音短语及汉字单字大部分匹配
     * @return
     * @author Huangzq
     * @date 2023/2/21 11:10
     */
    @GetMapping(value = "/shard/query")
    public ResultResponse<Map<String, Object>> query(@RequestParam("index") String index,
                                                     @RequestParam("query") String query,
                                                     @RequestParam(name = "filter", defaultValue = "") String filter,
                                                     @RequestParam(value = "size", defaultValue = "20") Integer size,
                                                     @RequestParam(value = "page", defaultValue = "0") Integer page,
                                                     @RequestParam(value = "explain", defaultValue = "false") Boolean explain,
                                                     @RequestParam(name = "type", defaultValue = "fuzzy") String type) {
        Map<String, Object> name = shardIndexMergeLoadService.concurrentSearch(index, query, filter, type, size, page, explain);
        return ResultResponse.success(name);
    }

    public static ConcurrentMap<String, Semaphore> SEMAPHORE_MAP = new ConcurrentHashMap<>();

    @GetMapping(value = "/shard/create")
    public ResultResponse<List<String>> create(@RequestParam("index") String index) {
        Semaphore semaphore = SEMAPHORE_MAP.computeIfAbsent(index,s -> new Semaphore(1));
        if(semaphore.tryAcquire()){
            try {
                List<String> list = shardIndexMergeService.initShardIndexForPage(index);
                return ResultResponse.success(list);
            }catch (Exception e) {
                log.error("索引生成失败",e);
                return ResultResponse.fail("101","索引生成失败，稍后再试");
            }finally {
                semaphore.release();
            }
        }else{
            return ResultResponse.fail("101","索引生成中，勿重复提交");
        }
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
