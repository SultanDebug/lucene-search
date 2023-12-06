package com.hzq.search.controller;

import com.hzq.common.dto.ResultResponse;
import com.hzq.search.service.ShardIndexMergeLoadService;
import com.hzq.search.service.ShardIndexMergeService;
import com.hzq.search.service.shard.IndexInfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
@Api(tags = "分片索引接口")
@RestController
@Slf4j
@Scope
public class ShardController {
    public static ConcurrentMap<String, Semaphore> SEMAPHORE_MAP = new ConcurrentHashMap<>();
    @Autowired
    private ShardIndexMergeLoadService shardIndexMergeLoadService;
    @Autowired
    private ShardIndexMergeService shardIndexMergeService;
    @Autowired
    private IndexInfoService indexInfoService;

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
     *                complex fuzzy  single_fuzzy  pinyin single_name
     * @return
     * @author Huangzq
     * @date 2023/2/21 11:10
     */
    @ApiOperation(value = "搜索接口")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name = "index", value = "索引名", required = true),
            @ApiImplicitParam(paramType = "query", name = "query", value = "搜索词", required = true),
            @ApiImplicitParam(paramType = "query", name = "filter", value = "过滤条件", required = true),
            @ApiImplicitParam(paramType = "query", name = "size", value = "页大小", required = true),
            @ApiImplicitParam(paramType = "query", name = "page", value = "页码", required = true),
            @ApiImplicitParam(paramType = "query", name = "explain", value = "是否解析", required = true),
            @ApiImplicitParam(paramType = "query", name = "type", value = "查询方式detail-companyid查询  prefix-精确查询，前缀、term  fuzzy-模糊查询  complex-复合查询，single_name-term优化模糊查询，拼音短语及汉字单字大部分匹配", required = true),
    })
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

    @ApiOperation(value = "索引创建")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name = "index", value = "索引名", required = true)
    })
    @GetMapping(value = "/shard/create")
    public ResultResponse<List<String>> create(@RequestParam("index") String index) {
        Semaphore semaphore = SEMAPHORE_MAP.computeIfAbsent(index, s -> new Semaphore(1));
        if (semaphore.tryAcquire()) {
            try {
                List<String> list = shardIndexMergeService.initShardIndexForPage(index);
                return ResultResponse.success(list);
            } catch (Exception e) {
                log.error("索引生成失败", e);
                return ResultResponse.fail("101", "索引生成失败，稍后再试");
            } finally {
                semaphore.release();
            }
        } else {
            return ResultResponse.fail("101", "索引生成中，勿重复提交");
        }
    }

    @ApiOperation(value = "索引状态查询")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name = "index", value = "索引名", required = true)
    })
    @GetMapping(value = "/shard/status")
    public ResultResponse<Map<String, String>> status(@RequestParam("index") String index) {
        Semaphore semaphore = SEMAPHORE_MAP.computeIfAbsent(index, s -> new Semaphore(1));

        Map<String, String> map = new HashMap<>();
        if (semaphore.tryAcquire()) {
            try {
                map.put("lock", "锁空闲");
            } catch (Exception e) {
                log.error("位置异常", e);
            } finally {
                semaphore.release();
            }
        } else {
            map.put("lock", "索引生成中");
        }

        indexInfoService.getCurIndex(index, map);

        return ResultResponse.success(map);
    }

    @ApiOperation(value = "索引信息查询")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name = "index", value = "索引名", required = true)
    })
    @GetMapping(value = "/shard/info")
    public ResultResponse<List<List<Map<String, Object>>>> info(@RequestParam("index") String index) {
        List<List<Map<String, Object>>> list = shardIndexMergeLoadService.concurrentInfo(index);
        return ResultResponse.success(list);
    }

    @PostMapping(value = "/shard/add/{index}")
    @Deprecated
    public ResultResponse<String> add(@PathVariable("index") String index, @RequestBody Map<String, Object> data) {
        shardIndexMergeService.addIndex(index, data);
        return ResultResponse.success();
    }

    @GetMapping(value = "/shard/merge")
    @Deprecated
    public ResultResponse<String> merge(@RequestParam("index") String index) {
        shardIndexMergeService.indexMerge(index);
        return ResultResponse.success();
    }
}
