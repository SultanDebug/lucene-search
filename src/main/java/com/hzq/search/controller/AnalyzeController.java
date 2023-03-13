package com.hzq.search.controller;

import com.hzq.search.analyzer.MyCnPinyinAnalyzer;
import com.hzq.search.service.ResultResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 分词查询接口
 *
 * @author Huangzq
 * @description
 * @date 2023/3/13 16:25
 */
@RestController
@Api(tags = "分词查询接口")
public class AnalyzeController {


    @ApiOperation(value = "拼音分词")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name = "smart", value = "智能分词，true、false", required = true),
            @ApiImplicitParam(paramType = "query", name = "query", value = "搜索词", required = true)
    })
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

    @ApiOperation(value = "概率分词")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name = "smart", value = "智能分词，true、false", required = true),
            @ApiImplicitParam(paramType = "query", name = "query", value = "搜索词", required = true)
    })
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
}