package com.hzq.researchtest.test.analyzer;

import com.hzq.researchtest.test.analyzer.MyAnalyzer;
import com.hzq.researchtest.analyzer.MyPinyinAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.beans.factory.InitializingBean;
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
 * @date 2022/11/19 13:52
 */
//@Service
@Slf4j
public class LuceneService implements InitializingBean {
    /*@Autowired
    private BirdExtendAnalyzer birdExtendAnalyzer;*/
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

    private Analyzer ikAnalyzer;
    private IndexWriterConfig conf;

    private IndexSearcher searcher;

    IndexWriter indexWriter = null;

    private void initIndex(MyAnalyzer myAnalyzer) throws Exception {
//        this.analyzer = myAnalyzer;
        this.analyzer = new MyPinyinAnalyzer(true);
        this.ikAnalyzer = new IKAnalyzer(true);
        conf = new IndexWriterConfig(analyzer);
        indexWriter = new IndexWriter(directory, conf);
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
        int id = 0;
        for (String re : res) {
            log.info(re);
            Document doc = new Document();
            doc.add(new TextField("name", re, Field.Store.YES));
            doc.add(new TextField("id", String.valueOf(id++), Field.Store.YES));
            indexWriter.addDocument(doc);
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

    public List<String> search(String filed, String query) throws IOException {

        TokenStream tokenStream = ikAnalyzer.tokenStream(filed, query);
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
            builder.add(new Term(filed, term));
        }

        Query tmpQuery = builder.build();

        // 返回Top5的结果
        int resultTopN = 5;

        long start = System.nanoTime();
        ScoreDoc[] scoreDocs = searcher.search(tmpQuery, resultTopN).scoreDocs;
        long end = System.nanoTime();
        log.info("召回花费：{}", end - start);
        List<String> list1 = new ArrayList<>();
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            list1.add(doc.get("id") + "==>" + doc.get("name"));
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
        initIndex(null);
        //查询阶段
        IndexReader reader = DirectoryReader.open(indexWriter);
        searcher = new IndexSearcher(reader);
    }
}
