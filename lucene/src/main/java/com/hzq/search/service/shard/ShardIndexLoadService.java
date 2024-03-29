package com.hzq.search.service.shard;

import com.google.common.collect.Lists;
import com.hzq.search.analyzer.MySingleCharAnalyzer;
import com.hzq.search.config.FieldDef;
import com.hzq.search.enums.QueryTypeEnum;
import com.hzq.search.service.shard.query.QueryBuildAbstract;
import com.hzq.search.service.shard.query.QueryManager;
import com.hzq.search.util.Position;
import com.hzq.search.util.TextRelevance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分片数据查询
 * 问题：需要定制查询
 *
 * @author Huangzq
 * @date 2022/12/2 10:02
 */
@Slf4j
public class ShardIndexLoadService {
    /**
     * 文件索引存储地址
     */
    private String fsPath;
    /**
     * 分片数量
     */
    private int shardNum;

    /**
     * 文件名称
     */
    private String fsPathName;

    /**
     * 文件索引地址
     */
    private Directory fsDirectory;
    /**
     * 文件索引查询器
     */
    private IndexSearcher fsSearcher;

    /**
     * 文件索引io
     */
    private IndexReader fsReader;

    private int recallSize = 10;

    private static boolean reScore(String query, String name, Map<String, String> map) {
        Long score = 0L;
        TextRelevance trie = new TextRelevance();
        List<String> tokens = getSingleWordToken(query);
        Set<String> segments = new HashSet<>(tokens);
        for (String token : segments) {
            if (!org.springframework.util.StringUtils.isEmpty(token)) {
                trie.addKeyword(token);
            }
        }
        Map<String, List<Position>> termPostionMap = new HashMap<>();
        trie.parsetText4Pos(name, (begin, end, emit) -> termPostionMap.computeIfAbsent(emit, t -> new ArrayList<>()).add(new Position(1, begin)));
        int matchCount = 0;
        int distance = 5;
        int prePosition = 0;
        for (String token : tokens) {
            if (termPostionMap.containsKey(token)) {
                matchCount++;
                List<Position> matchs = termPostionMap.get(token);
                if (matchCount == 1) {
                    prePosition = matchs.stream().map(Position::getOffset).min(Comparator.comparing(Integer::intValue)).orElse(0);
                    continue;
                }
                int finalPrePosition = prePosition;
                int slop = matchs.stream().map(item -> Math.abs(item.getOffset() - finalPrePosition)).min(Comparator.comparing(Integer::intValue)).orElse(0);
                prePosition = slop + prePosition;
                if (slop != 1) {
                    distance--;
                }
                if (distance <= 0) {
                    break;
                }
            }
        }
        if (tokens.size() - matchCount <= 2 && distance >= 0) {
            score = score | (long) 5 << 52;
            score = score | (long) (7 - (tokens.size() - matchCount)) << 48;
            score = score | (long) distance << 44;
            map.put("score", String.valueOf(score));
            return true;
        }

        return false;
    }

    private static List<String> getSingleWordToken(String query) {
        try (MySingleCharAnalyzer analyzer = new MySingleCharAnalyzer()) {
            List<String> res = new ArrayList<>();
            TokenStream tokenStream = analyzer.tokenStream("", query);
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                res.add(termAtt.toString());
            }
            tokenStream.close();//必须
            return res;
        } catch (Exception e) {
            log.error("模糊查询分词异常：{}", query, e);
        }
        return new ArrayList<>();
    }

    public static void main(String[] args) {
        Map<String, String> termPostionMap = new HashMap<>();
        reScore("东莞明翔智能科技有限公司", "东莞市明鑫翔智能科技有限公司", termPostionMap);
    }

    public void setShardNum(int shardNum, int recallSize) {
        this.recallSize = recallSize;
        this.shardNum = shardNum;
    }

    /**
     * 地址组成 主路径+分片id+主索引/增量索引名
     */
    public void setFsPath(String fsPath, int shardNum, String fsPathName) {
        this.fsPath = fsPath + File.separator + shardNum + File.separator + fsPathName;
        this.shardNum = shardNum;
        this.fsPathName = fsPathName;
    }

    /**
     * Description:
     * 索引修改重新加载更新
     *
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:12
     */
    public void indexUpdate(String path) {
        if (StringUtils.isNotBlank(path)) {
            this.setFsPath(path, shardNum, fsPathName);
        }
        if (StringUtils.isBlank(fsPath)) {
            log.error("索引参数未配置！");
            return;
        }
        try {
            if (fsReader != null) {
                fsReader.close();
            }
            fsDirectory = FSDirectory.open(Paths.get(fsPath));
            fsReader = DirectoryReader.open(fsDirectory);
            fsSearcher = new IndexSearcher(fsReader);
            //fsSearcher.setSimilarity(new BooleanSimilarity());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Description:
     * 搜索
     * 主索引和增量索引召回
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:14
     */
    public List<Map<String, String>> shardSearch(String index, Map<String, FieldDef> fieldMap, String query, String filter, AtomicLong totle, String type, Boolean explain) {
        if (StringUtils.isBlank(fsPath)) {
            log.error("索引参数未配置！");
            return null;
        }
        try {
            if (fsDirectory == null) {
                fsDirectory = FSDirectory.open(Paths.get(fsPath));
                fsReader = DirectoryReader.open(fsDirectory);
                fsSearcher = new IndexSearcher(fsReader);
                //fsSearcher.setSimilarity(new BooleanSimilarity());
            }
            return this.sesarch(index, fsSearcher, fieldMap, query, filter, totle, type, explain);
        } catch (Exception e) {
            log.error("查询失败：{}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Description:
     * 索引信息
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:14
     */
    public List<Map<String, Object>> shardInfo(String index) {
        if (StringUtils.isBlank(fsPath)) {
            log.error("索引参数未配置！");
            return null;
        }
        try {
            if (fsDirectory == null) {
                fsDirectory = FSDirectory.open(Paths.get(fsPath));
                fsReader = DirectoryReader.open(fsDirectory);
                fsSearcher = new IndexSearcher(fsReader);
                //fsSearcher.setSimilarity(new BooleanSimilarity());
            }

            // 获取索引段信息
            SegmentInfos segmentInfos = SegmentInfos.readLatestCommit(fsDirectory);
            List<Map<String, Object>> list = new ArrayList<>();
            for (SegmentCommitInfo segmentCommitInfo : segmentInfos) {
                Map<String, Object> map = new HashMap<>();
                // 打印每个索引段的信息
                map.put("Segment Name: ", segmentCommitInfo.info.name);
                map.put("Segment Max Doc: ", segmentCommitInfo.info.maxDoc());
                map.put("Segment Doc Count: ", segmentCommitInfo.info.maxDoc() - segmentCommitInfo.getDelCount());
                list.add(map);
            }

            return list;
        } catch (Exception e) {
            log.error("查询失败：{}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 查询索引文档
     * 第一步：创建一个Directory对象，也就是索引库存放的位置。
     * 第二步：创建一个indexReader对象，需要指定Directory对象。
     * 第三步：创建一个IndexSearcher对象，需要指定IndexReader对象
     * 第四步：创建一个TermQuery对象，指定查询的域和查询的关键词。
     * 第五步：执行查询。
     * 第六步：返回查询结果。遍历查询结果并输出。
     * 第七步：关闭IndexReader对象
     * <p>
     * lucene查询语法：
     * 1、使用Term去查询，Term的格式为  field:value  value将被视为一个整体，不分词。或者，field:"value"  value将被视为词组，会被分词
     * 2、使用Field去查询，  field:value  ，不会对查询词进行分词，如果不指定field，则直接查默认field
     * 3、通配符模糊查询， field:v?l*   ,其中？匹配一个字符，*匹配多个字符，实例可以匹配value、vilu，但是要注意，通配符不能出现在第一个字符上
     * 4、相似度查询， field:value~   会查询出和value相似的内容，例如aalue、valua
     * 5、指定距离查询，field:"hello world"~10    对这两个单词进行分词，如果两个词的间距在10个字符以内，则算匹配
     * 6、范围查询，field:[N TO M}  [中括号表示包含，{大括号表示不包含， TO 关键字，必须大写，实例表示值在 N 到 M 之间，包含 N，不包含M
     * 7、权重优先级，field:"hello^4  world",搜索和排序时，优先考虑hello
     * 8、Term操作符组合多条件，AND、OR、NOT、+、-  ，都必须多个term才能用，且都大写
     * 8.1、AND : field1:hello AND field2:world  表示field1字段必须有hello，同时field2字段也必须有world
     * 8.2、OR ： field1:hello OR field2:world  表示field1字段有hello，或者，field2字段有world
     * 8.3、NOT ： field1:hello NOT field2:world  表示field1字段必须有hello，同时，field2字段必须不能有world，NOT不能单独用
     * 8.4、+ ： +field1:hello  field2:world  表示field1:hello必须有满足，类似AND ,如果term前面不带+-，则是说明可满足可不满足
     * 8.5、- ： -field1:hello  field2:world  表示field1:hello必须不能满足，类似NOT
     * 9、分组，(field1:hello AND field2:world) OR field3:hello  ,多个term时可以进行组合
     * 10、特殊字符 + - && || ! ( ) { } [ ] ^ " ~ * ? : \ ，如果查询文本总就有特殊字符，则需要用\进行转义。
     * QueryParser.escape(q)  可转换q中含有查询关键字的字符！如：* ,? 等
     */
    private List<Map<String, String>> sesarch(String index,
                                              IndexSearcher searcher,
                                              Map<String, FieldDef> fieldMap,
                                              String query,
                                              String filter,
                                              AtomicLong totle,
                                              String type,
                                              Boolean explain) throws Exception {
        QueryTypeEnum queryTypeEnum = QueryTypeEnum.findByType(type);
        QueryBuildAbstract queryBuild = QueryManager.getQueryBuild(index);
        if (queryBuild == null || queryTypeEnum == null) {
            log.error("参数{}，{}异常或未注册", type, index);
            return Lists.newArrayList();
        }
        if (StringUtils.isEmpty(query)) {
            log.error("参数{}为空，不支持处理", query);
            return Lists.newArrayList();
        }
        Pair<String, Query> queryPair = queryBuild.buildQuery(searcher, query, filter, fieldMap, queryTypeEnum);

        if (queryPair == null) {
            return Lists.newArrayList();
        }

        List<Map<String, String>> list = new ArrayList<>();

        Sort sort = new Sort(new SortField(null, SortField.Type.SCORE, false),
                new SortField("company_score", SortField.Type.DOUBLE, true));
        TopDocs prefixDocs = searcher.search(queryPair.getRight(), recallSize, sort, true, false);

        totle.addAndGet(prefixDocs.totalHits);
        ScoreDoc[] scoreDocs4 = prefixDocs.scoreDocs;
        for (int i = 0; i < scoreDocs4.length; i++) {
            ScoreDoc scoreDoc = scoreDocs4[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String, String> map = new HashMap<>();
            map.put("score", String.valueOf(scoreDoc.score));
            map.put("shard", String.valueOf(shardNum));
            if (explain) {
                map.put("explain", searcher.explain(queryPair.getRight(), scoreDoc.doc).toString());
            }
            map.put("data_type", queryPair.getLeft());
//            if(!reScore(query, doc.get("name"), map)){
//                continue;
//            }
            fieldMap.values().stream()
                    .filter(o -> o.getStored() == 1)
                    .forEach(o -> map.put(o.getFieldName(), doc.get(o.getFieldName())));
            list.add(map);
        }

        return list;
    }
}
