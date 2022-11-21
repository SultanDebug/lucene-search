package com.hzq.researchtest.service;

import com.bird.segment.core.common.Token;
import com.bird.segment.extend.BirdExtendAnalyzer;
import com.hzq.researchtest.test.TermSearchQueryTest;
import com.hzq.researchtest.test.analyzer.MyAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/19 13:52
 */
@Service
@Slf4j
public class LuceneService implements InitializingBean {
    @Autowired
    private BirdExtendAnalyzer birdExtendAnalyzer;
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
    private Analyzer analyzer ;
    private IndexWriterConfig conf ;

    private IndexSearcher searcher;

    IndexWriter indexWriter = null;

    private void initIndex(MyAnalyzer myAnalyzer) throws Exception{
        this.analyzer = new IKAnalyzer(false);
//        this.analyzer = myAnalyzer;
        conf = new IndexWriterConfig(analyzer);
        indexWriter = new IndexWriter(directory, conf);
        // 索引阶段结束
        Connection con = getCon();
        List<String> res = new ArrayList<>();
        Statement stmt = con.createStatement();
        for (int i = 0; i < 200; i++) {
            List<String> tmps = query(i,stmt);
            if(tmps.size()<10000){break;}
            res.addAll(tmps);
        }


        if(stmt!=null){
            stmt.close();
        }
        if(con!=null){
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

    public List<String> query(int offset,Statement stmt){
        int start = offset*10000;
        String  Sql="select name from bird_search_db.ads_qxb_enterprise_search_sort_filter_wide limit "+start+" , 10000";

        List<String> lists = new ArrayList<>();
        try {

            ResultSet rs=stmt.executeQuery(Sql);

            while(rs.next()){
                lists.add(rs.getString("name").substring(1));
            }

            if(rs!=null){
                rs.close();
            }

        } catch (SQLException e) {
            System.out.println("query:创建Statement失败");
            e.printStackTrace();
        }
        return lists;
    }

    public Connection getCon(){
        Connection conn=null;
        String driver="com.mysql.cj.jdbc.Driver";
        String url="jdbc:mysql://bird-search-db-test.qizhidao.net:3306/bird_search_db?characterEncoding=utf8&useSSL=false&allowMultiQueries=true";
        try{
            //注册（加载）驱动程序
            Class.forName(driver);
            try{
                //获取数据库接
                conn= DriverManager.getConnection(url,"bird_search_ro","NTN8Mw2mGGsgs7IDUBea");
            }catch(SQLException e){
                System.out.println("getConnection:连接数据库失败");
                e.printStackTrace();
            }
        }catch(ClassNotFoundException e){
            System.out.println("getConnection:加载驱动失败");
        }

        return conn;
    }

    private void init(MyAnalyzer analyzer) throws Exception {
        initIndex(analyzer);
        //查询阶段
        IndexReader reader = DirectoryReader.open(indexWriter);
        searcher = new IndexSearcher(reader);
    }

    public List<String> search(String filed,String query) throws IOException {
        List<Token> list = birdExtendAnalyzer.probabilitySegment(query);

        List<String> terms = list.stream().map(Token::getTerm).collect(Collectors.toList());

        String[] param = new String[terms.size()];

        for (int i = 0; i < terms.size(); i++) {
            param[i] = terms.get(i);
        }

        Query tmpQuery = new PhraseQuery(filed, param);

        // 返回Top5的结果
        int resultTopN = 5;

        ScoreDoc [] scoreDocs = searcher.search(tmpQuery, resultTopN).scoreDocs;
        List<String> list1 = new ArrayList<>();
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list1.add(doc.get("id")+"==>"+doc.get("name"));
        }

        return list1;

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        /*BirdExtendAnalyzer birdExtendAnalyzer = new BirdExtendAnalyzer();
        String modelDirNew = "D:\\MavenRepo\\com\\bird\\segment\\bird-segment-server\\2.0.6-RELEASE\\segment";
        Set<ExtendType> probExt = new HashSet();
        Set<ExtendType> compExt = new HashSet();
        probExt.add(ExtendType.CASCADE);
        compExt.add(ExtendType.CASCADE);
        compExt.add(ExtendType.SYNONYM);
        compExt.add(ExtendType.HYPERNYM);
        compExt.add(ExtendType.ARIBIC_PARSE);
        birdExtendAnalyzer.init(modelDirNew, probExt, compExt);

        MyAnalyzer analyzer = new MyAnalyzer(birdExtendAnalyzer);

        TermSearchQueryTest termQueryTest = new TermSearchQueryTest();
        termQueryTest.doDemo(analyzer);*/

        //ik
        this.init(null);
    }
}
