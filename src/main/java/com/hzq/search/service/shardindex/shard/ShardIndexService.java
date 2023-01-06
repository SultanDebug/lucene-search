package com.hzq.search.service.shardindex.shard;

import com.hzq.search.analyzer.MyJianpinAnalyzer;
import com.hzq.search.analyzer.MyOnlyPinyinAnalyzer;
import com.hzq.search.config.FieldDef;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
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
    //索引数据修改需要通知查询器更新索引信息
    private ShardIndexLoadService shardIndexLoadService;

    //分片数
    private int shardNum;
    //文件系统索引
    private String fsPath;

    //内存映射索引
    private String incrPath;

    //数据id、自增
    private Long id = 0L;

    //字段根据配置生成分词信息
    private List<Pair<String, Analyzer>> fieldConfigs;

    public void setShardNum(int shardNum) {
        this.shardNum = shardNum;
    }

    public void setShardIndexLoadService(ShardIndexLoadService shardIndexLoadService) {
        this.shardIndexLoadService = shardIndexLoadService;
    }
    //地址组成 主路径+分片id+主索引/增量索引名
    public void setFsPath(String fsPath, int shardNum, String fsPathName) {
        this.fsPath = fsPath + "\\" + shardNum + "\\" + fsPathName;
        this.shardNum = shardNum;
    }
    //地址组成 主路径+分片id+主索引/增量索引名
    public void setIncrPath(String incrPath, int shardNum, String incrPathName) {
        this.incrPath = incrPath + "\\" + shardNum + "\\" + incrPathName;
        this.shardNum = shardNum;
    }

    public void setFieldConfigs(Map<String, FieldDef> defMap) {
        this.fieldConfigs = this.getCurIndexFieldConfig(defMap);
    }
    /**
     * Description:
     *  索引配置信息
     *      字段分词、io等
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:23
     */
    private IndexWriterConfig getConfig(List<Pair<String, Analyzer>> fieldConfig) {
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();

        for (Pair<String, Analyzer> pair : fieldConfig) {
            fieldAnalyzers.put(pair.getLeft(), pair.getRight());
        }

        // 对于没有指定的分词器的字段，使用标准分词器
        PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new IKAnalyzer(true), fieldAnalyzers);

        IndexWriterConfig conf = new IndexWriterConfig(analyzer);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return conf;
    }
    /**
     * Description:
     *  索引主、增量合并
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:24
     */
    public Long indexMerge() {
        if (StringUtils.isBlank(fsPath) || StringUtils.isBlank(incrPath) || shardIndexLoadService == null) {
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

            if (fsFiles == null || fsFiles.length == 0 || Arrays.stream(fsFiles).noneMatch(o -> o.startsWith("segments"))) {
                log.info("分片【" + shardNum + "】主索引数据文件缺失");
                return 0L;
            }

            if (incrFiles == null || incrFiles.length == 0 || Arrays.stream(incrFiles).noneMatch(o -> o.startsWith("segments"))) {
                log.info("分片【" + shardNum + "】增量索引数据文件缺失");
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
            log.info("分片【" + shardNum + "】合并数量：{},花费：{}", merge, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引合并失败：{}", e.getMessage(), e);
        } finally {
            try {
                //fsDirectory.close();
                if (mainIndex != null) {
                    mainIndex.close();
                }

                //incrDirectory.close();
                if (incrIndex != null) {
                    incrIndex.close();
                }
            } catch (Exception e) {
                log.error("合并索引关闭失败：{}", e.getMessage(), e);
            }
        }

        return merge;
    }

    private void deleteDocForUpdate(String id, IndexWriter mainIndex, IndexWriter incrIndex) {
        try {
            long main = mainIndex.deleteDocuments(new Term("id", id));
            long incr = incrIndex.deleteDocuments(new Term("id", id));
            mainIndex.flush();
            mainIndex.commit();
            log.info("分片【" + shardNum + "】主索引删除：{}，增量索引删除：{}", main, incr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Description:
     *  索引数据新增
     * @param fieldMap 字段配置信息
     * @param data 文档数据
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:25
     */
    public void addIndex(Map<String, FieldDef> fieldMap, String id, Map<String, String> data) {
        if (StringUtils.isBlank(fsPath) || StringUtils.isBlank(incrPath) || shardIndexLoadService == null) {
            log.error("索引参数未配置！");
            return;
        }
        Directory fsDirectory;
        IndexWriter mainIndex = null;

        Directory incrDirectory;
        IndexWriter incrIndex = null;
        try {
            IndexWriterConfig mainConf = this.getConfig(fieldConfigs);
            fsDirectory = FSDirectory.open(Paths.get(fsPath));
            mainIndex = new IndexWriter(fsDirectory, mainConf);

            IndexWriterConfig incrConf = this.getConfig(fieldConfigs);
            incrDirectory = MMapDirectory.open(Paths.get(incrPath));
            incrIndex = new IndexWriter(incrDirectory, incrConf);

            deleteDocForUpdate(id, mainIndex, incrIndex);

            long start = System.currentTimeMillis();
            Document doc = this.docByConfig(fieldMap, data);

            incrIndex.addDocument(doc);
            incrIndex.flush();
            incrIndex.commit();

            //通知load端重新加载
            shardIndexLoadService.indexUpdate(false);

            log.info("索引更新结束：{}", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引初始化失败：{}", e.getMessage(), e);
        } finally {
            try {
                //fsDirectory.close();
                if (mainIndex != null) {
                    mainIndex.close();
                }

                //incrDirectory.close();
                if (incrIndex != null) {
                    incrIndex.close();
                }
            } catch (Exception e) {
                log.error("更新索引关闭失败：{}", e.getMessage(), e);
            }
        }

    }
    /**
     * Description:
     *  索引初始化
     * @param fieldMap 字段配置信息
     * @param res 当前分片文档集合
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:26
     */
    public void initIndex(Map<String, FieldDef> fieldMap, List<Map<String, String>> res) {
        if (StringUtils.isBlank(fsPath) || StringUtils.isBlank(incrPath) || shardIndexLoadService == null) {
            log.error("索引参数未配置！");
            return;
        }
        Directory fsDirectory = null;
        IndexWriter mainIndex = null;
        try {
            IndexWriterConfig conf = this.getConfig(fieldConfigs);
            fsDirectory = FSDirectory.open(Paths.get(fsPath));
            mainIndex = new IndexWriter(fsDirectory, conf);

            // 每次运行demo先清空索引目录中的索引文件
            mainIndex.deleteAll();
            mainIndex.flush();
            mainIndex.commit();

            long start = System.currentTimeMillis();
            log.info("索引构建开始：{}", start);
            long sysTime = System.currentTimeMillis();

            for (Map<String, String> map : res) {
                Document document = this.docByConfig(fieldMap, map);
                mainIndex.addDocument(document);
                if (id % 10000 == 0) {
                    log.info("加载进度：{}，花费：{}", id, System.currentTimeMillis() - sysTime);
                    sysTime = System.currentTimeMillis();
                }
            }

            /*for (Map<String,String> re : res) {
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
            }*/
            mainIndex.flush();
            mainIndex.commit();

            shardIndexLoadService.indexUpdate(true);

            log.info("索引构建结束：{}", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引初始化失败：{}", e.getMessage(), e);
        } finally {
            try {
                //fsDirectory.close();
                if (mainIndex != null) {
                    mainIndex.close();
                }
            } catch (Exception e) {
                log.error("初始化索引关闭失败：{}", e.getMessage(), e);
            }

        }
    }
    /**
     * Description:
     *  字段分词信息生成
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:27
     */
    private List<Pair<String, Analyzer>> getCurIndexFieldConfig(Map<String, FieldDef> defMap) {
        List<Pair<String, Analyzer>> list = new ArrayList<>();

        for (Map.Entry<String, FieldDef> entry : defMap.entrySet()) {
            Analyzer analyzer = null;
            switch (entry.getValue().getAnalyzerType()) {
                case 1:
                    // 字段使用ik分词器
                    analyzer = new IKAnalyzer(true);
                    break;
                case 2:
                    // 拼音字段使用标准分词器
                    analyzer = new MyOnlyPinyinAnalyzer(true);
                    break;
                case 3:
                    // 简拼字段使用标准分词器
                    analyzer = new MyJianpinAnalyzer();
                    break;
                default:
                    // 默认使用ik分词器
                    analyzer = new IKAnalyzer(true);
                    break;
            }
            list.add(Pair.of(entry.getKey(), analyzer));
        }
        return list;
    }
    /**
     * Description:
     *  根据配置和原始数据生成搜索文档
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:27
     */
    private Document docByConfig(Map<String, FieldDef> defMap, Map<String, String> valMap) {
        Document doc = new Document();
        for (Map.Entry<String, FieldDef> entry : defMap.entrySet()) {
            String val = valMap.get(entry.getKey());
            if (StringUtils.isBlank(val)) {
                //看是否为衍生字段
                if(entry.getValue().getDbFieldFlag()==1){
                    continue;
                }
                val = valMap.get(entry.getValue().getParentField());
                if(StringUtils.isBlank(val)){
                    continue;
                }
            }
            Field.Store store = entry.getValue().getStored() == 0 ? Field.Store.NO : Field.Store.YES;
            IndexableField textField = null;
            switch (entry.getValue().getFieldType()) {
                case 1:
                    textField = new TextField(entry.getKey(), val, store);
                    break;
                case 2:
                    textField = new StringField(entry.getKey(), val, store);
                    break;
                case 3:
                    //todo 未支持
                    //textField = new IntRange(entry.getKey(), entry.getValue(), store);
                    //break;
                default:
                    break;
            }

            //文档标准化，特殊字符、大小写、简繁、全半等
            doc.add(textField);
        }
        return doc;
    }
}
