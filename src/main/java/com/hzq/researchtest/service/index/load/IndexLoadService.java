package com.hzq.researchtest.service.index.load;

import com.hzq.researchtest.analyzer.MyJianpinAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/28 16:39
 */
@Service
@Slf4j
public class IndexLoadService {

    private Directory directory;

    private IndexSearcher searcher;

    {
        try {
            directory = FSDirectory.open(Paths.get("C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\myindex"));

            IndexReader reader = DirectoryReader.open(directory);
            searcher = new IndexSearcher(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Analyzer pinyinAnalyzer;

    private Analyzer ikAnalyzer = new IKAnalyzer(true);
    private MyJianpinAnalyzer jianpinAnalyzer = new MyJianpinAnalyzer();

    public List<String> search(String fieldId, String query) throws Exception {
        //降维 布尔or 召回
        QueryParser queryParser = new QueryParser("name", ikAnalyzer);
        Query parse = queryParser.parse("name:" + query);

        //原词分词召回
        PhraseQuery tmpQuery = this.phraseQuery("name", query);

        //原词拼音分词召回
        PhraseQuery pinyinQuery = this.phrasePinyinQuery("pinyin", query);

        //简拼全词召回
        TermQuery jianpinQuery = this.jianpinQuery("jianpin", query);

        BooleanQuery booleanQuery = this.seggestQuery(query);

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

        long start = System.nanoTime();
        ScoreDoc[] scoreDocs = searcher.search(tmpQuery, resultTopN).scoreDocs;
        long end = System.nanoTime();
        log.info("【名称】召回花费：{}", end - start);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【名称】命中==>" + doc.get("id") + "==>" + doc.get("name")+ "==>" + scoreDoc.score);
        }

        long start1 = System.nanoTime();
        ScoreDoc[] scoreDocs1 = searcher.search(pinyinQuery, resultTopN).scoreDocs;
        long end1 = System.nanoTime();
        log.info("【拼音】召回花费：{}", end1 - start1);
        for (int i = 0; i < scoreDocs1.length; i++) {
            ScoreDoc scoreDoc = scoreDocs1[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【拼音】命中==>" + doc.get("id") + "==>" + doc.get("name")+ "==>" + scoreDoc.score);
        }

        long start2 = System.nanoTime();
        ScoreDoc[] scoreDocs2 = searcher.search(jianpinQuery, resultTopN).scoreDocs;
        long end2 = System.nanoTime();
        log.info("【简拼】召回花费：{}", end2 - start2);
        for (int i = 0; i < scoreDocs2.length; i++) {
            ScoreDoc scoreDoc = scoreDocs2[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【简拼】命中==>" + doc.get("id") + "==>" + doc.get("name")+ "==>" + scoreDoc.score);
        }

        long start3 = System.nanoTime();
        ScoreDoc[] scoreDocs3 = searcher.search(parse, resultTopN).scoreDocs;
        long end3 = System.nanoTime();
        log.info("【原词或召回】召回花费：{}", end3 - start3);
        for (int i = 0; i < scoreDocs3.length; i++) {
            ScoreDoc scoreDoc = scoreDocs3[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【原词或召回】命中==>" + doc.get("id") + "==>" + doc.get("name")+ "==>" + scoreDoc.score);
        }

        return list;

    }

    private BooleanQuery seggestQuery(String query) throws Exception{
        //降维 布尔or 召回
        QueryParser queryParser = new QueryParser("name", ikAnalyzer);
        Query parse = queryParser.parse("name:" + query);

        //原词分词召回
        PhraseQuery tmpQuery = this.phraseQuery("name", query);

        //原词拼音分词召回
        PhraseQuery pinyinQuery = this.phrasePinyinQuery("pinyin", query);

        //简拼全词召回
        TermQuery jianpinQuery = this.jianpinQuery("jianpin", query);

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(tmpQuery, BooleanClause.Occur.SHOULD);
        builder.add(jianpinQuery, BooleanClause.Occur.SHOULD);
        builder.add(pinyinQuery, BooleanClause.Occur.SHOULD);
        builder.add(parse, BooleanClause.Occur.SHOULD);

        return builder.build();
    }

    private TermQuery jianpinQuery(String fieldId, String query) throws IOException {
        return new TermQuery(new Term(fieldId,query));
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

    private PhraseQuery phraseQuery(String fieldId, String query) throws IOException {
        TokenStream tokenStream = ikAnalyzer.tokenStream(fieldId, query);
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();//必须
        List<String> terms = new ArrayList<>();
        while (tokenStream.incrementToken()) {
            terms.add(termAtt.toString());
        }
        tokenStream.close();//必须
        String[] param = new String[terms.size()];
        for (int i = 0; i < terms.size(); i++) {
            param[i] = terms.get(i);
        }

        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        for (String term : terms) {
            builder.add(new Term(fieldId, term));
        }

        builder.setSlop(3);

        return builder.build();
    }
}
