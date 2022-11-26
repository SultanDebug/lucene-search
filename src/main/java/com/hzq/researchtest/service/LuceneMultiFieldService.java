package com.hzq.researchtest.service;

import com.hzq.researchtest.analyzer.MyJianpinAnalyzer;
import com.hzq.researchtest.analyzer.MyOnlyPinyinAnalyzer;
import com.hzq.researchtest.analyzer.MyPinyinAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/19 13:52
 */
@Service
@Slf4j
public class LuceneMultiFieldService implements InitializingBean {
    private Directory directory;

    {
        try {
            // 每次运行demo先清空索引目录中的索引文件
            File file = new File("C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\myindex");
            for (File listFile : file.listFiles()) {
                listFile.delete();
            }
            directory = FSDirectory.open(Paths.get("C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\myindex"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 空格分词器
    private Analyzer pinyinAnalyzer;

    private Analyzer ikAnalyzer;
    private MyJianpinAnalyzer jianpinAnalyzer;
    private IndexWriterConfig conf;

    private IndexSearcher searcher;

    IndexWriter indexWriter = null;

    private void initIndex() throws Exception {
        this.pinyinAnalyzer = new MyOnlyPinyinAnalyzer(true);
        this.ikAnalyzer = new IKAnalyzer(true);
        this.jianpinAnalyzer = new MyJianpinAnalyzer();

        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();

        // name字段使用ik分词器
        fieldAnalyzers.put("name", ikAnalyzer);
        // 拼音字段使用标准分词器
        fieldAnalyzers.put("pinyin", pinyinAnalyzer);
        // 简拼字段使用标准分词器
        fieldAnalyzers.put("jianpin", jianpinAnalyzer);
        // 对于没有指定的分词器的字段，使用标准分词器
        PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(ikAnalyzer, fieldAnalyzers);

        conf = new IndexWriterConfig(analyzer);

        //conf = new IndexWriterConfig(pinyinAnalyzer);
        indexWriter = new IndexWriter(directory, conf);
        // 索引阶段结束
        Connection con = getCon();
        List<String> res = new ArrayList<>();
        Statement stmt = con.createStatement();
        int size = 10000;
        for (int i = 0; i < 100; i++) {
            List<String> tmps = query(i, stmt, size);
            if (tmps.size() < size) {
                break;
            }
            res.addAll(tmps);
        }
        stmt.close();
        con.close();

        long start = System.currentTimeMillis();
        log.info("索引构建开始：{}", start);
        int id = 0;
        long sysTime = System.currentTimeMillis();
        for (String re : res) {
            Document doc = new Document();
            //文档标准化，特殊字符、大小写、简繁、全半等
            doc.add(new TextField("name", re, Field.Store.YES));
            doc.add(new TextField("pinyin", re, Field.Store.NO));
            doc.add(new TextField("jianpin", re, Field.Store.NO));
            doc.add(new TextField("id", String.valueOf(id++), Field.Store.YES));
            indexWriter.addDocument(doc);
            if(id%10000==0){
                log.info("加载进度：{}，花费：{}",id,System.currentTimeMillis()-sysTime);
                sysTime = System.currentTimeMillis();
            }
        }
        indexWriter.commit();
        log.info("索引构建结束：{}", System.currentTimeMillis() - start);
    }

    public List<String> query(int offset, Statement stmt, int size) {
        int start = offset * size;
        String Sql = "select name from bird_search_db.ads_qxb_enterprise_search_sort_filter_wide limit " + start + " , " + size;

        List<String> lists = new ArrayList<>();
        try {
            ResultSet rs = stmt.executeQuery(Sql);
            while (rs.next()) {
                lists.add(rs.getString("name").substring(1));
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lists;
    }

    public Connection getCon() {
        Connection conn = null;
        String driver = "com.mysql.cj.jdbc.Driver";
        String url = "jdbc:mysql://bird-search-db-test.qizhidao.net:3306/bird_search_db?characterEncoding=utf8&useSSL=false&allowMultiQueries=true";
        try {
            //注册（加载）驱动程序
            Class.forName(driver);
            //获取数据库接
            conn = DriverManager.getConnection(url, "bird_search_ro", "NTN8Mw2mGGsgs7IDUBea");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return conn;
    }

    public List<String> search(String fieldId, String query) throws Exception {

        /*PhraseQuery tmpQuery = this.phraseQuery("name", query);

        PhraseQuery pinyinQuery = this.phrasePinyinQuery("pinyin", query);

        TermQuery jianpinQuery = this.jianpinQuery("jianpin", query);*/

        BooleanQuery booleanQuery = this.seggestQuery(query);

        // 返回Top5的结果
        int resultTopN = 5;

        long start = System.nanoTime();
        ScoreDoc[] scoreDocs = searcher.search(booleanQuery, resultTopN).scoreDocs;
        long end = System.nanoTime();
        log.info("【布尔】召回花费：{}", end - start);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【布尔】命中==>" + doc.get("id") + "==>" + doc.get("name"));
        }

        /*long start = System.nanoTime();
        ScoreDoc[] scoreDocs = searcher.search(tmpQuery, resultTopN).scoreDocs;
        long end = System.nanoTime();
        log.info("【名称】召回花费：{}", end - start);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【名称】命中==>" + doc.get("id") + "==>" + doc.get("name"));
        }

        long start1 = System.nanoTime();
        ScoreDoc[] scoreDocs1 = searcher.search(pinyinQuery, resultTopN).scoreDocs;
        long end1 = System.nanoTime();
        log.info("【拼音】召回花费：{}", end1 - start1);
        for (int i = 0; i < scoreDocs1.length; i++) {
            ScoreDoc scoreDoc = scoreDocs1[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【拼音】命中==>" + doc.get("id") + "==>" + doc.get("name"));
        }

        long start2 = System.nanoTime();
        ScoreDoc[] scoreDocs2 = searcher.search(jianpinQuery, resultTopN).scoreDocs;
        long end2 = System.nanoTime();
        log.info("【简拼】召回花费：{}", end2 - start2);
        for (int i = 0; i < scoreDocs2.length; i++) {
            ScoreDoc scoreDoc = scoreDocs2[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list.add("【简拼】命中==>" + doc.get("id") + "==>" + doc.get("name"));
        }*/

        return list;

    }

    private BooleanQuery seggestQuery(String query) throws Exception{
        PhraseQuery tmpQuery = this.phraseQuery("name", query);

        PhraseQuery pinyinQuery = this.phrasePinyinQuery("pinyin", query);

        TermQuery jianpinQuery = this.jianpinQuery("jianpin", query);
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(tmpQuery, BooleanClause.Occur.SHOULD);
        builder.add(jianpinQuery, BooleanClause.Occur.SHOULD);
        builder.add(pinyinQuery, BooleanClause.Occur.SHOULD);

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

    @Override
    public void afterPropertiesSet() throws Exception {
        //ik
        initIndex();
        //查询阶段
        IndexReader reader = DirectoryReader.open(indexWriter);
        searcher = new IndexSearcher(reader);
    }
}
