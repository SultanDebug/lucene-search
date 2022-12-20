package com.hzq.researchtest.service.single.shard;

import com.hzq.researchtest.config.FieldDef;
import com.hzq.researchtest.service.single.shard.query.QueryBuild;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分片数据查询
 * 问题：需要定制查询
 *
 * @author Huangzq
 * @description
 * @date 2022/12/2 10:02
 */
@Slf4j
public class ShardSingleIndexLoadService {
    //文件索引存储地址
    private String fsPath;
    //分片数量
    private int shardNum;

    //文件索引地址
    private Directory fsDirectory;
    //文件索引查询器
    private IndexSearcher fsSearcher;

    //文件索引io
    private IndexReader fsReader;

    //内存映射索引路径
    private Analyzer ikAnalyzer = new IKAnalyzer(true);

    public void setShardNum(int shardNum) {
        this.shardNum = shardNum;
    }

    //地址组成 主路径+分片id+主索引/增量索引名
    public void setFsPath(String fsPath, int shardNum, String fsPathName) {
        this.fsPath = fsPath + "\\" + shardNum + "\\" + fsPathName;
        this.shardNum = shardNum;
    }

    /**
     * Description:
     * 索引修改重新加载更新
     *
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:12
     */
    public void indexUpdate() {
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
    public List<Map<String, String>> search(Map<String, FieldDef> fieldMap, String query, AtomicLong totle) {
        if (StringUtils.isBlank(fsPath)) {
            log.error("索引参数未配置！");
            return null;
        }
        try {
            if (fsDirectory == null) {
                fsDirectory = FSDirectory.open(Paths.get(fsPath));
                fsReader = DirectoryReader.open(fsDirectory);
                fsSearcher = new IndexSearcher(fsReader);
            }

            List<Map<String, String>> fsList = this.sugSearch(fsSearcher, fieldMap, query, totle);

            return fsList;
        } catch (Exception e) {
            log.error("查询失败：{}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Description:
     * 分片数据搜索
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:16
     */
    private List<Map<String, String>> search(IndexSearcher searcher, Map<String, FieldDef> fieldMap, String query, AtomicLong totle) throws Exception {
        //降维 布尔or 召回
        QueryParser queryParser = new QueryParser("name", ikAnalyzer);
        Query parse = queryParser.parse("name:" + query);

        Query prefixQuery = new PrefixQuery(new Term("name", query));

        //原词分词召回
//        PhraseQuery tmpQuery = this.phraseQuery("name", query);
        BooleanQuery tmpQuery = this.termsQuery("name", query);

        //原词拼音分词召回
        PhraseQuery pinyinQuery = this.phrasePinyinQuery("pinyin", query);

        //简拼全词召回
        TermQuery jianpinQuery = this.jianpinQuery("jianpin", query);

        Query booleanQuery = QueryBuild.sugQuery(query);

        // 返回Top5的结果
        int resultTopN = 5;

        /*long start = System.nanoTime();
        ScoreDoc[] scoreDocs = searcher.search(booleanQuery, resultTopN).scoreDocs;
        long end = System.nanoTime();
        log.info("【布尔】召回花费：{}", end - start);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【布尔】命中==>" + doc.get("id") + "==>" + doc.get("name")+ "==>" + scoreDoc.score);
        }*/
        List<Map<String, String>> list = new ArrayList<>();

        long start = System.nanoTime();

        long start4 = System.nanoTime();
        TopDocs prefixDocs = searcher.search(prefixQuery, resultTopN);
        totle.addAndGet(prefixDocs.totalHits);
        ScoreDoc[] scoreDocs4 = prefixDocs.scoreDocs;
        long end4 = System.nanoTime();
        //log.info("分片【"+shardNum+"】【原词或召回】召回花费：{}", end3 - start3);
        for (int i = 0; i < scoreDocs4.length; i++) {
            ScoreDoc scoreDoc = scoreDocs4[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String, String> map = new HashMap<>();
            map.put("score", String.valueOf(scoreDoc.score));
            map.put("shard", String.valueOf(shardNum));
            map.put("type", "前缀");
            fieldMap.values().stream().filter(o -> o.getDbFieldFlag() == 1).forEach(
                    o -> {
                        map.put(o.getFieldName(), doc.get(o.getFieldName()));
                    }
            );
            list.add(map);
        }

        TopDocs nameDocs = searcher.search(tmpQuery, resultTopN);
        totle.addAndGet(nameDocs.totalHits);
        ScoreDoc[] scoreDocs = nameDocs.scoreDocs;
        long end = System.nanoTime();
        //log.info("分片【"+shardNum+"】【名称】召回花费：{}", end - start);
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String, String> map = new HashMap<>();
            map.put("score", String.valueOf(scoreDoc.score));
            map.put("shard", String.valueOf(shardNum));
            map.put("type", "名称");
            fieldMap.values().stream().filter(o -> o.getDbFieldFlag() == 1).forEach(
                    o -> {
                        map.put(o.getFieldName(), doc.get(o.getFieldName()));
                    }
            );
            list.add(map);
        }

        long start1 = System.nanoTime();
        TopDocs pinyinDocs = searcher.search(pinyinQuery, resultTopN);
        totle.addAndGet(pinyinDocs.totalHits);
        ScoreDoc[] scoreDocs1 = pinyinDocs.scoreDocs;
        long end1 = System.nanoTime();
        //log.info("分片【"+shardNum+"】【拼音】召回花费：{}", end1 - start1);
        for (int i = 0; i < scoreDocs1.length; i++) {
            ScoreDoc scoreDoc = scoreDocs1[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String, String> map = new HashMap<>();
            map.put("score", String.valueOf(scoreDoc.score));
            map.put("shard", String.valueOf(shardNum));
            map.put("type", "拼音");
            fieldMap.values().stream().filter(o -> o.getDbFieldFlag() == 1).forEach(
                    o -> {
                        map.put(o.getFieldName(), doc.get(o.getFieldName()));
                    }
            );
            list.add(map);
        }

        long start2 = System.nanoTime();
        TopDocs jianpinDocs = searcher.search(jianpinQuery, resultTopN);
        totle.addAndGet(jianpinDocs.totalHits);
        ScoreDoc[] scoreDocs2 = jianpinDocs.scoreDocs;
        long end2 = System.nanoTime();
        //log.info("分片【"+shardNum+"】【简拼】召回花费：{}", end2 - start2);
        for (int i = 0; i < scoreDocs2.length; i++) {
            ScoreDoc scoreDoc = scoreDocs2[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String, String> map = new HashMap<>();
            map.put("score", String.valueOf(scoreDoc.score));
            map.put("shard", String.valueOf(shardNum));
            map.put("type", "简拼");
            fieldMap.values().stream().filter(o -> o.getDbFieldFlag() == 1).forEach(
                    o -> {
                        map.put(o.getFieldName(), doc.get(o.getFieldName()));
                    }
            );
            list.add(map);
        }

        /*long start3 = System.nanoTime();
        ScoreDoc[] scoreDocs3 = searcher.search(parse, resultTopN).scoreDocs;
        long end3 = System.nanoTime();
        //log.info("分片【"+shardNum+"】【原词或召回】召回花费：{}", end3 - start3);
        for (int i = 0; i < scoreDocs3.length; i++) {
            ScoreDoc scoreDoc = scoreDocs3[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String,String> map = new HashMap<>();
            map.put("score",String.valueOf(scoreDoc.score));
            map.put("shard",String.valueOf(shardNum));
            map.put("type","or");
            fieldMap.values().stream().filter(o->o.getDbFieldFlag()==1).forEach(
                    o->{
                        map.put(o.getFieldName(),doc.get(o.getFieldName()));
                    }
            );
            list.add(map);
        }*/

        log.info("分片【" + shardNum + "】召回花费：{}", System.nanoTime() - start);

        return list;

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
     *
     * lucene查询语法：
     * 1、使用Term去查询，Term的格式为  field:value  value将被视为一个整体，不分词。或者，field:"value"  value将被视为词组，会被分词
     * 2、使用Field去查询，  field:value  ，不会对查询词进行分词，如果不指定field，则直接查默认field
     * 3、通配符模糊查询， field:v?l*   ,其中？匹配一个字符，*匹配多个字符，实例可以匹配value、vilu，但是要注意，通配符不能出现在第一个字符上
     * 4、相似度查询， field:value~   会查询出和value相似的内容，例如aalue、valua
     * 5、指定距离查询，field:"hello world"~10    对这两个单词进行分词，如果两个词的间距在10个字符以内，则算匹配
     * 6、范围查询，field:[N TO M}  [中括号表示包含，{大括号表示不包含， TO 关键字，必须大写，实例表示值在 N 到 M 之间，包含 N，不包含M
     * 7、权重优先级，field:"hello^4  world",搜索和排序时，优先考虑hello
     * 8、Term操作符组合多条件，AND、OR、NOT、+、-  ，都必须多个term才能用，且都大写
     *    8.1、AND : field1:hello AND field2:world  表示field1字段必须有hello，同时field2字段也必须有world
     *    8.2、OR ： field1:hello OR field2:world  表示field1字段有hello，或者，field2字段有world
     *    8.3、NOT ： field1:hello NOT field2:world  表示field1字段必须有hello，同时，field2字段必须不能有world，NOT不能单独用
     *    8.4、+ ： +field1:hello  field2:world  表示field1:hello必须有满足，类似AND ,如果term前面不带+-，则是说明可满足可不满足
     *    8.5、- ： -field1:hello  field2:world  表示field1:hello必须不能满足，类似NOT
     * 9、分组，(field1:hello AND field2:world) OR field3:hello  ,多个term时可以进行组合
     * 10、特殊字符 + - && || ! ( ) { } [ ] ^ " ~ * ? : \ ，如果查询文本总就有特殊字符，则需要用\进行转义。
     *     QueryParser.escape(q)  可转换q中含有查询关键字的字符！如：* ,? 等
     * */
    private List<Map<String, String>> sugSearch(IndexSearcher searcher, Map<String, FieldDef> fieldMap, String query, AtomicLong totle) throws Exception {
        Query query1 = QueryBuild.sugQuery(query);

        // 返回Top5的结果
        int resultTopN = 10;
        List<Map<String, String>> list = new ArrayList<>();
        long start = System.nanoTime();
        TopDocs prefixDocs = searcher.search(query1, resultTopN);
        totle.addAndGet(prefixDocs.totalHits);
        ScoreDoc[] scoreDocs4 = prefixDocs.scoreDocs;
        for (int i = 0; i < scoreDocs4.length; i++) {
            ScoreDoc scoreDoc = scoreDocs4[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String, String> map = new HashMap<>();
            map.put("score", String.valueOf(scoreDoc.score));
            map.put("shard", String.valueOf(shardNum));
            map.put("type", "前缀");
            fieldMap.values().stream()
                    .filter(o -> o.getStored() == 1)
                    .forEach(o -> map.put(o.getFieldName(), doc.get(o.getFieldName())));
            list.add(map);
        }

        log.info("分片【" + shardNum + "】召回花费：{}", System.nanoTime() - start);

        return list;

    }


    private TermQuery jianpinQuery(String fieldId, String query) throws IOException {
        return new TermQuery(new Term(fieldId, query));
    }

    private PhraseQuery phrasePinyinQuery(String fieldId, String query) throws IOException {
        String[] param = query.split(" ");
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        for (String term : param) {
            builder.add(new Term(fieldId, term));
        }
        builder.setSlop(3);
        return builder.build();
    }

    private BooleanQuery termsQuery(String fieldId, String query) throws IOException {
        TokenStream tokenStream = ikAnalyzer.tokenStream(fieldId, query);
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
        tokenStream.reset();//必须
        List<Pair<String, Integer>> terms = new ArrayList<>();
        while (tokenStream.incrementToken()) {
            terms.add(Pair.of(termAtt.toString(), offsetAttribute.startOffset()));
        }
        tokenStream.close();//必须
        String[] param = new String[terms.size()];
        for (int i = 0; i < terms.size(); i++) {
            param[i] = terms.get(i).getLeft();
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Pair<String, Integer> term : terms) {
            TermQuery termQuery = new TermQuery(new Term(fieldId, term.getLeft()));
            builder.add(termQuery, BooleanClause.Occur.MUST);
        }

        return builder.build();
    }

    private PhraseQuery phraseQuery(String fieldId, String query) throws IOException {
        TokenStream tokenStream = ikAnalyzer.tokenStream(fieldId, query);
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
        tokenStream.reset();//必须
        List<Pair<String, Integer>> terms = new ArrayList<>();
        while (tokenStream.incrementToken()) {
            terms.add(Pair.of(termAtt.toString(), offsetAttribute.startOffset()));
        }
        tokenStream.close();//必须
        String[] param = new String[terms.size()];
        for (int i = 0; i < terms.size(); i++) {
            param[i] = terms.get(i).getLeft();
        }

        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        for (Pair<String, Integer> term : terms) {
            builder.add(new Term(fieldId, term.getLeft()), term.getRight());
        }

        builder.setSlop(10);

        return builder.build();
    }
}
