package com.hzq.researchtest.service.shardindex.shard;

import com.hzq.researchtest.analyzer.MyJianpinAnalyzer;
import com.hzq.researchtest.analyzer.MyOnlyPinyinAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.nio.file.Paths;
import java.util.*;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/2 10:01
 */
@Slf4j
public class ShardIndexService {
    private ShardIndexLoadService shardIndexLoadService;

    private int shardNum;
    //文件系统索引
    private String fsPath ;

    //内存映射索引
    private String incrPath ;

    private Long id = 0L;

    private List<Pair<String,Analyzer>> fieldConfigs;

    {
        fieldConfigs = getCurIndexFieldConfig();
    }

    public void setShardNum(int shardNum) {
        this.shardNum = shardNum;
    }

    public void setShardIndexLoadService(ShardIndexLoadService shardIndexLoadService) {
        this.shardIndexLoadService = shardIndexLoadService;
    }

    public void setFsPath(String fsPath, int shardNum,String fsPathName) {
        this.fsPath = fsPath + "\\"+shardNum+ "\\"+fsPathName;
        this.shardNum = shardNum;
    }

    public void setIncrPath(String incrPath,int shardNum,String incrPathName) {
        this.incrPath = incrPath+ "\\"+shardNum+ "\\"+incrPathName;
        this.shardNum = shardNum;
    }



    private IndexWriterConfig getConfig(List<Pair<String,Analyzer>> fieldConfig){
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();

        for (Pair<String, Analyzer> pair : fieldConfig) {
            fieldAnalyzers.put(pair.getLeft(),pair.getRight());
        }

        // 对于没有指定的分词器的字段，使用标准分词器
        PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new IKAnalyzer(true), fieldAnalyzers);

        IndexWriterConfig conf = new IndexWriterConfig(analyzer);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return conf;
    }

    private List<Pair<String,Analyzer>> getCurIndexFieldConfig(){
        List<Pair<String,Analyzer>> list = new ArrayList<>();
        // name字段使用ik分词器
        list.add(Pair.of("name", new IKAnalyzer(true)));
        // 拼音字段使用标准分词器
        list.add(Pair.of("pinyin", new MyOnlyPinyinAnalyzer(true)));
        // 简拼字段使用标准分词器
        list.add(Pair.of("jianpin", new MyJianpinAnalyzer()));
        return list;
    }

    public Long indexMerge() {
        if(StringUtils.isBlank(fsPath) || StringUtils.isBlank(incrPath) || shardIndexLoadService ==null){
            log.error("索引参数未配置！");
            return null;
        }
        long start = System.currentTimeMillis();
        Directory fsDirectory = null;
        IndexWriter mainIndex = null;

        Directory incrDirectory = null;
        IndexWriter incrIndex = null;
        Long merge = null;
        try {
            IndexWriterConfig mainConf = this.getConfig(fieldConfigs);
            fsDirectory = FSDirectory.open(Paths.get(fsPath));

            IndexWriterConfig incrConf = this.getConfig(fieldConfigs);
            incrDirectory = MMapDirectory.open(Paths.get(incrPath));

            String[] fsFiles = fsDirectory.listAll();
            String[] incrFiles = incrDirectory.listAll();

            if(fsFiles==null || fsFiles.length==0 || Arrays.stream(fsFiles).noneMatch(o->o.startsWith("segments"))){
                log.info("分片【"+shardNum+"】主索引数据文件缺失");
                return 0L;
            }

            if(incrFiles==null || incrFiles.length==0 || Arrays.stream(incrFiles).noneMatch(o->o.startsWith("segments"))){
                log.info("分片【"+shardNum+"】增量索引数据文件缺失");
                return 0L;
            }

            mainIndex = new IndexWriter(fsDirectory, mainConf);

            merge = mainIndex.addIndexes(incrDirectory);
            mainIndex.flush();
            mainIndex.commit();

            incrIndex = new IndexWriter(incrDirectory, incrConf);

            incrIndex.deleteAll();
            incrIndex.flush();
            incrIndex.commit();

            //通知load端重新加载
            shardIndexLoadService.indexUpdate(false);
            log.info("分片【"+shardNum+"】合并数量：{},花费：{}",merge,System.currentTimeMillis()-start);
        }catch (Exception  e){
            log.error("索引合并失败：{}",e.getMessage(),e);
        }finally {
            try {
                //fsDirectory.close();
                if(mainIndex!=null){
                    mainIndex.close();
                }

                //incrDirectory.close();
                if(incrIndex!=null){
                    incrIndex.close();
                }
            }catch (Exception e){
                log.error("合并索引关闭失败：{}",e.getMessage(),e);
            }
        }

        return merge;
    }

    private void deleteDocForUpdate(String id,IndexWriter mainIndex,IndexWriter incrIndex){
        try {
            long main = mainIndex.deleteDocuments(new Term("id",id));
            long incr = incrIndex.deleteDocuments(new Term("id", id));
            mainIndex.flush();
            mainIndex.commit();
            log.info("分片【"+shardNum+"】主索引删除：{}，增量索引删除：{}",main,incr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void addIndex(Long id , String data) {
        if(StringUtils.isBlank(fsPath) || StringUtils.isBlank(incrPath) || shardIndexLoadService ==null){
            log.error("索引参数未配置！");
            return;
        }
        Directory fsDirectory;
        IndexWriter mainIndex = null;

        Directory incrDirectory;
        IndexWriter incrIndex = null;
        try {
            IndexWriterConfig mainConf = this.getConfig(fieldConfigs);
            fsDirectory =FSDirectory.open(Paths.get(fsPath));
            mainIndex = new IndexWriter(fsDirectory, mainConf);

            IndexWriterConfig incrConf = this.getConfig(fieldConfigs);
            incrDirectory =MMapDirectory.open(Paths.get(incrPath));
            incrIndex = new IndexWriter(incrDirectory, incrConf);

            deleteDocForUpdate(String.valueOf(id),mainIndex,incrIndex);

            long start = System.currentTimeMillis();
            Document doc = new Document();
            //文档标准化，特殊字符、大小写、简繁、全半等
            doc.add(new TextField("name", data, Field.Store.YES));
            doc.add(new TextField("pinyin", data, Field.Store.NO));
            doc.add(new StringField("jianpin", data, Field.Store.NO));
            doc.add(new StringField("id", id.toString(), Field.Store.YES));

            mainIndex.deleteDocuments(new Term("id",String.valueOf(id)));

            incrIndex.addDocument(doc);
            incrIndex.flush();
            incrIndex.commit();

            //通知load端重新加载
            shardIndexLoadService.indexUpdate(false);

            log.info("索引更新结束：{}", System.currentTimeMillis() - start);
        }catch (Exception e){
            log.error("索引初始化失败：{}",e.getMessage(),e);
        }finally {
            try {
                //fsDirectory.close();
                mainIndex.close();

                //incrDirectory.close();
                incrIndex.close();
            }catch (Exception e){
                log.error("更新索引关闭失败：{}",e.getMessage(),e);
            }
        }

    }

    public void initIndex(List<String> res) {
        if(StringUtils.isBlank(fsPath) || StringUtils.isBlank(incrPath) || shardIndexLoadService ==null){
            log.error("索引参数未配置！");
            return;
        }
        Directory fsDirectory = null;
        IndexWriter mainIndex = null;
        try {
            IndexWriterConfig conf = this.getConfig(fieldConfigs);
            fsDirectory =FSDirectory.open(Paths.get(fsPath));
            mainIndex = new IndexWriter(fsDirectory, conf);

            // 每次运行demo先清空索引目录中的索引文件
            mainIndex.deleteAll();
            mainIndex.flush();
            mainIndex.commit();

            long start = System.currentTimeMillis();
            log.info("索引构建开始：{}", start);
            long sysTime = System.currentTimeMillis();
            for (String re : res) {
                Document doc = new Document();
                //文档标准化，特殊字符、大小写、简繁、全半等
                doc.add(new TextField("name", re, Field.Store.YES));
                doc.add(new TextField("pinyin", re, Field.Store.NO));
                doc.add(new StringField("jianpin", re, Field.Store.NO));
                doc.add(new StringField("id", String.valueOf(id++), Field.Store.YES));
                mainIndex.addDocument(doc);
                if(id%10000==0){
                    log.info("加载进度：{}，花费：{}",id,System.currentTimeMillis()-sysTime);
                    sysTime = System.currentTimeMillis();
                }
            }
            mainIndex.flush();
            mainIndex.commit();

            shardIndexLoadService.indexUpdate(true);

            log.info("索引构建结束：{}", System.currentTimeMillis() - start);
        }catch (Exception e){
            log.error("索引初始化失败：{}",e.getMessage(),e);
        }finally {
            try {
                //fsDirectory.close();
                mainIndex.close();
            }catch (Exception e){
                log.error("初始化索引关闭失败：{}",e.getMessage(),e);
            }

        }
    }
}
