package com.hzq.researchtest.test;

import com.bird.segment.core.common.Token;
import com.bird.segment.extend.BirdExtendAnalyzer;
import com.bird.segment.extend.ExtendType;
import com.bird.segment.extend.IExtendAnalyzer;
import com.bird.segment.init.AnalyzerIniter;
import com.hzq.researchtest.test.analyzer.MyAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 17:36
 */
public class TermQueryTest {
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
    private Analyzer analyzer = new WhitespaceAnalyzer();
    private IndexWriterConfig conf = new IndexWriterConfig(analyzer);

    IndexWriter indexWriter = null;

    private void initIndex() throws Exception{
        indexWriter = new IndexWriter(directory, conf);
        Document doc ;
        // 0
        doc = new Document();
        doc.add(new TextField("content", "同义词标签数量", Field.Store.YES));
        doc.add(new TextField("author", "author1", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 1
        doc = new Document();
        doc.add(new TextField("content", "我是中国人", Field.Store.YES));
        doc.add(new TextField("author", "author2", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 2
        doc = new Document();
        doc.add(new TextField("content", "上下位词标签数量", Field.Store.YES));
        doc.add(new TextField("author", "author3", Field.Store.YES));

        indexWriter.addDocument(doc);
        // 3
        doc = new Document();
        doc.add(new TextField("content", "a c e", Field.Store.YES));
        doc.add(new TextField("author", "author4", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 4
        doc = new Document();
        doc.add(new TextField("content", "h", Field.Store.YES));
        doc.add(new TextField("author", "author5", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 5
        doc = new Document();
        doc.add(new TextField("content", "c e", Field.Store.YES));
        doc.add(new TextField("author", "author6", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 6
        doc = new Document();
        doc.add(new TextField("content", "c a e", Field.Store.YES));
        doc.add(new TextField("author", "author7", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 7
        doc = new Document();
        doc.add(new TextField("content", "f", Field.Store.YES));
        doc.add(new TextField("author", "author8", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 8
        doc = new Document();
        doc.add(new TextField("content", "b c d e c e", Field.Store.YES));
        doc.add(new TextField("author", "author9", Field.Store.YES));
        indexWriter.addDocument(doc);
        // 9
        doc = new Document();
        doc.add(new TextField("content", "a c e a b c", Field.Store.YES));
        doc.add(new TextField("author", "author10", Field.Store.YES));
        indexWriter.addDocument(doc);
        indexWriter.commit();
        // 索引阶段结束
    }

    private void doDemo() throws Exception {
        initIndex();

        //查询阶段
        IndexReader reader = DirectoryReader.open(indexWriter);
        IndexSearcher searcher = new IndexSearcher(reader);

        // 查询条件, 找出 域名为"content", 域值中包含"a"的文档（Document）
        Query query = new TermQuery(new Term("content", "国家"));

        // 返回Top5的结果
        int resultTopN = 5;

        ScoreDoc [] scoreDocs = searcher.search(query, resultTopN).scoreDocs;

        System.out.println("Total Result Number: "+scoreDocs.length+"");
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            // 输出满足查询条件的 文档号
            System.out.println("result"+i+": 文档"+scoreDoc.doc+"");
        }

    }

    public static void main(String[] args) throws Exception{

        BirdExtendAnalyzer birdExtendAnalyzer = new BirdExtendAnalyzer();
        String modelDirNew = "D:\\MavenRepo\\com\\bird\\segment\\bird-segment-server\\2.0.6-RELEASE\\segment";
        Set<ExtendType> probExt = new HashSet();
        Set<ExtendType> compExt = new HashSet();
        probExt.add(ExtendType.CASCADE);
        compExt.add(ExtendType.CASCADE);
        compExt.add(ExtendType.SYNONYM);
        compExt.add(ExtendType.HYPERNYM);
        compExt.add(ExtendType.ARIBIC_PARSE);
        birdExtendAnalyzer.init(modelDirNew, probExt, compExt);
        //IKAnalyzer ikAnalyzer = new IKAnalyzer();


        String arr[] = {"我是中国人","同义词数量爆棚"};
        MyAnalyzer analyzer = new MyAnalyzer(birdExtendAnalyzer);


        TermQueryTest termQueryTest = new TermQueryTest();
        termQueryTest.doDemo();
    }
}
