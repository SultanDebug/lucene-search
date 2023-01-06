package com.hzq.researchtest.test;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 17:36
 */
public class TermSearchQueryTest {
    private Directory directory;

    {
        try {
            // 每次运行demo先清空索引目录中的索引文件
            File file = new File("C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\myindex");
            for (File listFile : file.listFiles()) {
                listFile.delete();
            }
            directory = new MMapDirectory(Paths.get("C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\myindex"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 空格分词器
    private Analyzer analyzer;
    private IndexWriterConfig conf;

    IndexWriter indexWriter = null;

    private void initIndex() throws Exception {
        this.analyzer = new IKAnalyzer(false);
//        this.analyzer = myAnalyzer;
        conf = new IndexWriterConfig(analyzer);
        indexWriter = new IndexWriter(directory, conf);
        // 索引阶段结束
        Connection con = getCon();
        List<String> res = new ArrayList<>();
        Statement stmt = con.createStatement();
        for (int i = 0; i < 10; i++) {
            List<String> tmps = query(con, i, stmt);
            res.addAll(tmps);
        }


        if (stmt != null) {
            stmt.close();
        }
        if (con != null) {
            con.close();
        }

        int id = 0;
        for (String re : res) {
            Document doc = new Document();
            doc.add(new TextField("name", re, Field.Store.YES));
            doc.add(new TextField("id", String.valueOf(id++), Field.Store.YES));
            indexWriter.addDocument(doc);
        }
        indexWriter.commit();


    }

    public List<String> query(Connection conn, int offset, Statement stmt) {
        int start = offset * 10000;
        String Sql = "select name from db.table limit " + start + " , 10000";

        List<String> lists = new ArrayList<>();
        try {

            ResultSet rs = stmt.executeQuery(Sql);

            while (rs.next()) {
                lists.add(rs.getString("name").substring(1));
            }

            if (rs != null) {
                rs.close();
            }

        } catch (SQLException e) {
            System.out.println("query:创建Statement失败");
            e.printStackTrace();
        }
        return lists;
    }

    public Connection getCon() {
        Connection conn = null;
        String driver = "com.mysql.cj.jdbc.Driver";
        String url = "jdbc:mysql://host:3306/db?characterEncoding=utf8&useSSL=false&allowMultiQueries=true";
        try {
            //注册（加载）驱动程序
            Class.forName(driver);
            try {
                //获取数据库接
                conn = DriverManager.getConnection(url, "username", "pass");
            } catch (SQLException e) {
                System.out.println("getConnection:连接数据库失败");
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {
            System.out.println("getConnection:加载驱动失败");
        }

        return conn;
    }

    private void doDemo() throws Exception {
        initIndex();

        //查询阶段
        IndexReader reader = DirectoryReader.open(indexWriter);
        IndexSearcher searcher = new IndexSearcher(reader);

        // 查询条件, 找出 域名为"content", 域值中包含"a"的文档（Document）
        Query query = new TermQuery(new Term("name", "上海办事处"));

        query = new PhraseQuery("name", "上海", "办事处");

        // 返回Top5的结果
        int resultTopN = 5;

        ScoreDoc[] scoreDocs = searcher.search(query, resultTopN).scoreDocs;

        System.out.println("Total Result Number: " + scoreDocs.length + "");
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            System.out.println("result" + i + ": 文档" + doc.toString() + "");
        }

    }

    public static void main(String[] args) throws Exception {
        //ik
        TermSearchQueryTest termQueryTest = new TermSearchQueryTest();
        termQueryTest.doDemo();
    }
}
