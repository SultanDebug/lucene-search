package com.hzq.researchtest.service.multiindex;

import com.hzq.researchtest.analyzer.MyJianpinAnalyzer;
import com.hzq.researchtest.analyzer.MyOnlyPinyinAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @date 2022/11/30 15:28
 */
@Service
@Slf4j
public class MultiIndexService {
    @Autowired
    private MultiIndexLoadService multiIndexLoadService;

    private List<String> deleteIds;

    //文件系统索引
    private Directory fsDirectory;
    private IndexWriter mainIndex;
    private IndexWriterConfig conf;

    private String fsPath = "C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\multi_index\\fsindex";

    //内存映射索引
    private Directory mDirectory;
    private IndexWriter incrIndex;
    private IndexWriterConfig incrConf;
    private String incrPath = "C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\multi_index\\mmapindex";

    private MyJianpinAnalyzer jianpinAnalyzer;
    // 空格分词器
    private Analyzer pinyinAnalyzer;
    private Analyzer ikAnalyzer;



    private Long id = 0L;


    {
        try {

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
            conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            fsDirectory = FSDirectory.open(Paths.get(fsPath));
            mainIndex = new IndexWriter(fsDirectory, conf);


            Map<String, Analyzer> incrFieldAnalyzers = new HashMap<>();
            // name字段使用ik分词器
            incrFieldAnalyzers.put("name", ikAnalyzer);
            // 拼音字段使用标准分词器
            incrFieldAnalyzers.put("pinyin", pinyinAnalyzer);
            // 简拼字段使用标准分词器
            incrFieldAnalyzers.put("jianpin", jianpinAnalyzer);
            // 对于没有指定的分词器的字段，使用标准分词器
            PerFieldAnalyzerWrapper incrAnalyzer = new PerFieldAnalyzerWrapper(ikAnalyzer, incrFieldAnalyzers);

            incrConf = new IndexWriterConfig(incrAnalyzer);
            incrConf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            mDirectory = MMapDirectory.open(Paths.get(incrPath));
            incrIndex = new IndexWriter(mDirectory, incrConf);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void indexMerge() throws Exception {
        mainIndex.addIndexes(incrIndex.getDirectory());
        mainIndex.flush();
        mainIndex.commit();

        incrIndex.deleteAll();
        incrIndex.flush();
        incrIndex.commit();

        //通知load端重新加载
        multiIndexLoadService.indexUpdate(false);
    }


    public void addIndex(Long id , String data) throws Exception {
        long start = System.currentTimeMillis();
        Document doc = new Document();
        //文档标准化，特殊字符、大小写、简繁、全半等
        doc.add(new TextField("name", data, Field.Store.YES));
        doc.add(new TextField("pinyin", data, Field.Store.NO));
        doc.add(new TextField("jianpin", data, Field.Store.NO));
        doc.add(new TextField("id", String.valueOf(id), Field.Store.YES));

        mainIndex.deleteDocuments(new Term("id",String.valueOf(id)));

        incrIndex.addDocument(doc);
        incrIndex.flush();
        incrIndex.commit();

        //通知load端重新加载
        multiIndexLoadService.indexUpdate(false);

        log.info("索引更新结束：{}", System.currentTimeMillis() - start);
    }

    public void initIndex() throws Exception {
        // 每次运行demo先清空索引目录中的索引文件
        mainIndex.deleteAll();
        mainIndex.flush();
        mainIndex.commit();

        // 索引阶段结束
        Connection con = getCon();
        List<String> res = new ArrayList<>();
        Statement stmt = con.createStatement();
        int size = 100;
        for (int i = 0; i < 1; i++) {
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
        long sysTime = System.currentTimeMillis();
        for (String re : res) {
            Document doc = new Document();
            //文档标准化，特殊字符、大小写、简繁、全半等
            doc.add(new TextField("name", re, Field.Store.YES));
            doc.add(new TextField("pinyin", re, Field.Store.NO));
            doc.add(new TextField("jianpin", re, Field.Store.NO));
            doc.add(new TextField("id", String.valueOf(id++), Field.Store.YES));
            mainIndex.addDocument(doc);
            if(id%10000==0){
                log.info("加载进度：{}，花费：{}",id,System.currentTimeMillis()-sysTime);
                sysTime = System.currentTimeMillis();
            }
        }
        mainIndex.flush();
        mainIndex.commit();

        multiIndexLoadService.indexUpdate(true);

        log.info("索引构建结束：{}", System.currentTimeMillis() - start);
    }


    private List<String> query(int offset, Statement stmt, int size) {
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

    private Connection getCon() {
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
}
