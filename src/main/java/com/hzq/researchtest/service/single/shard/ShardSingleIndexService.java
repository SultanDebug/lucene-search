package com.hzq.researchtest.service.single.shard;

import com.hzq.researchtest.analyzer.MyAllPinyinAnalyzer;
import com.hzq.researchtest.analyzer.MyJianpinAnalyzer;
import com.hzq.researchtest.analyzer.MyOnlyPinyinAnalyzer;
import com.hzq.researchtest.config.FieldDef;
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
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.util.CollectionUtils;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.nio.file.Paths;
import java.util.*;

/**
 * 索引管理
 * 1.初始化
 * 2.新增、修改
 * 3.主索引、增量索引合并
 *
 * @author Huangzq
 * @description
 * @date 2022/12/2 10:01
 */
@Slf4j
public class ShardSingleIndexService {
    //索引数据修改需要通知查询器更新索引信息
    private ShardSingleIndexLoadService shardIndexLoadService;

    //分片数
    private int shardNum;
    //文件系统索引
    private String fsPath;

    //字段根据配置生成分词信息
    private List<Pair<String, Analyzer>> fieldConfigs;

    public void setShardNum(int shardNum) {
        this.shardNum = shardNum;
    }

    public void setShardIndexLoadService(ShardSingleIndexLoadService shardIndexLoadService) {
        this.shardIndexLoadService = shardIndexLoadService;
    }

    //地址组成 主路径+分片id+主索引/增量索引名
    public void setFsPath(String fsPath, int shardNum, String fsPathName) {
        this.fsPath = fsPath + "\\" + shardNum + "\\" + fsPathName;
        this.shardNum = shardNum;
    }

    public void setFieldConfigs(Map<String, FieldDef> defMap) {
        this.fieldConfigs = this.getCurIndexFieldConfig(defMap);
    }

    /**
     * Description:
     * 索引配置信息
     * 字段分词、io等
     *
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
        PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new IKAnalyzer(false), fieldAnalyzers);

        IndexWriterConfig conf = new IndexWriterConfig(analyzer);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return conf;
    }

    /**
     * Description:
     * 段合并
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:24
     */
    public Long indexMerge() {
        if (StringUtils.isBlank(fsPath) || shardIndexLoadService == null) {
            log.error("索引参数未配置！");
            return null;
        }
        long start = System.currentTimeMillis();
        Directory fsDirectory = null;
        IndexWriter mainIndex = null;

        Long merge = null;
        try {
            IndexWriterConfig mainConf = this.getConfig(fieldConfigs);
            fsDirectory = FSDirectory.open(Paths.get(fsPath));

            String[] fsFiles = fsDirectory.listAll();

            if (fsFiles == null || fsFiles.length == 0 || Arrays.stream(fsFiles).noneMatch(o -> o.startsWith("segments"))) {
                log.info("分片【" + shardNum + "】主索引数据文件缺失");
                return 0L;
            }

            mainIndex = new IndexWriter(fsDirectory, mainConf);

            mainIndex.forceMerge(1);
            mainIndex.flush();
            mainIndex.commit();

            //通知load端重新加载
            shardIndexLoadService.indexUpdate();
            log.info("分片【" + shardNum + "】合并数量：{},花费：{}", merge, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引合并失败：{}", e.getMessage(), e);
        } finally {
            try {
                //fsDirectory.close();
                if (mainIndex != null) {
                    mainIndex.close();
                }
            } catch (Exception e) {
                log.error("合并索引关闭失败：{}", e.getMessage(), e);
            }
        }

        return merge;
    }

    /**
     * Description:
     * 索引更新时需要清理存量数据
     * 修改或新增时，先删除再新增
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:24
     */
    private void deleteDocForUpdate(String id, IndexWriter mainIndex) {
        try {
            long main = mainIndex.deleteDocuments(new Term("id", id));
            mainIndex.flush();
            mainIndex.commit();
            log.info("分片【" + shardNum + "】主索引删除：{}", main);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Description:
     * 索引数据新增
     *
     * @param fieldMap 字段配置信息
     * @param data     文档数据
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:25
     */
    public void addIndex(Map<String, FieldDef> fieldMap, String id, Map<String, Object> data) {
        if (StringUtils.isBlank(fsPath) || shardIndexLoadService == null) {
            log.error("索引参数未配置！");
            return;
        }
        Directory fsDirectory;
        IndexWriter mainIndex = null;
        try {
            IndexWriterConfig mainConf = this.getConfig(fieldConfigs);
            fsDirectory = FSDirectory.open(Paths.get(fsPath));
            mainIndex = new IndexWriter(fsDirectory, mainConf);

            deleteDocForUpdate(id, mainIndex);

            long start = System.currentTimeMillis();
            Document doc = this.docByConfig(fieldMap, data);

            mainIndex.addDocument(doc);
            mainIndex.commit();
            mainIndex.flush();

            //通知load端重新加载
            shardIndexLoadService.indexUpdate();

            log.info("索引更新结束：{}", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引初始化失败：{}", e.getMessage(), e);
        } finally {
            try {
                //fsDirectory.close();
                if (mainIndex != null) {
                    mainIndex.close();
                }
            } catch (Exception e) {
                log.error("更新索引关闭失败：{}", e.getMessage(), e);
            }
        }

    }

    /**
     * Description:
     * 索引初始化
     *
     * @param fieldMap 字段配置信息
     * @param res      当前分片文档集合
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:26
     */
    public void initIndex(Map<String, FieldDef> fieldMap, List<Map<String, Object>> res) {
        if (StringUtils.isBlank(fsPath) || shardIndexLoadService == null) {
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

            for (Map<String, Object> map : res) {
                Document document = this.docByConfig(fieldMap, map);
                mainIndex.addDocument(document);
            }
            log.info("分片：{}，加载进度：{}，花费：{}", shardNum, res.get(res.size() - 1).get("id"), System.currentTimeMillis() - start);

            mainIndex.flush();
            mainIndex.commit();

            shardIndexLoadService.indexUpdate();

            log.info("分片：{}，加载进度：{}，索引构建结束：{}", shardNum, res.get(res.size() - 1).get("id"), System.currentTimeMillis() - start);
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
     * 仅限初始化使用
     */
    private IndexWriter mainIndex = null;
    private Directory fsDirectory = null;

    /**
     * Description:
     * 初始化前索引数据删除
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/14 17:55
     */
    public void deleteAll() {
        if (StringUtils.isBlank(fsPath) || shardIndexLoadService == null) {
            log.error("索引参数未配置！");
            return;
        }
        try {
            IndexWriterConfig conf = this.getConfig(fieldConfigs);
            fsDirectory = FSDirectory.open(Paths.get(fsPath));
            mainIndex = new IndexWriter(fsDirectory, conf);
            // 每次运行demo先清空索引目录中的索引文件
            mainIndex.deleteAll();
            mainIndex.flush();
            mainIndex.commit();
        } catch (Exception e) {
            log.error("索引删除失败：{}", e.getMessage(), e);
        }
    }


    /**
     * Description:
     * 索引文档添加
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/14 17:55
     */
    public void commitAll(Map<String, FieldDef> fieldMap, List<Map<String, Object>> res) {
        if (StringUtils.isBlank(fsPath) || shardIndexLoadService == null) {
            log.error("索引参数未配置！");
            return;
        }
        if (CollectionUtils.isEmpty(res)) {
            log.info("索引数据为空{}", shardNum);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            for (Map<String, Object> map : res) {
                Document document = this.docByConfig(fieldMap, map);
                mainIndex.addDocument(document);
            }
            log.info("分片：{}，加载进度：{}，索引构建结束：{}", shardNum, res.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引初始化失败：{}", e.getMessage(), e);
        }
    }

    /**
     * Description:
     * 初始化后数据提交及搜索端通知
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/14 17:54
     */
    public void noticeSearcher() {
        try {
            mainIndex.flush();
            mainIndex.commit();
            if (mainIndex != null) {
                mainIndex.close();
            }
            if (fsDirectory != null) {
                fsDirectory.close();
            }
        } catch (Exception e) {
            log.error("索引删除关闭失败：{}", e.getMessage(), e);
        }
        shardIndexLoadService.indexUpdate();
    }

    /**
     * Description:
     * 字段分词信息生成
     *
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
                    analyzer = new IKAnalyzer(false);
                    break;
                case 2:
                    // 拼音字段使用标准分词器
                    analyzer = new MyOnlyPinyinAnalyzer(true);
                    break;
                case 3:
                    // 简拼字段使用标准分词器
                    analyzer = new MyJianpinAnalyzer();
                    break;
                case 4:
                    // 简拼字段使用标准分词器
                    analyzer = new MyAllPinyinAnalyzer();
                    break;
                default:
                    // 默认使用ik分词器
                    analyzer = new IKAnalyzer(false);
                    break;
            }
            list.add(Pair.of(entry.getKey(), analyzer));
        }
        return list;
    }

    /**
     * Description:
     * 根据配置和原始数据生成搜索文档
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:27
     */
    private Document docByConfig(Map<String, FieldDef> defMap, Map<String, Object> valMap) {
        Document doc = new Document();
        for (Map.Entry<String, FieldDef> entry : defMap.entrySet()) {
            Object val = valMap.get(entry.getKey());
            if (Objects.isNull(val)) {
                //看是否为衍生字段
                if (entry.getValue().getDbFieldFlag() == 1) {
                    continue;
                }
                val = valMap.get(entry.getValue().getParentField()).toString();
                if (Objects.isNull(val)) {
                    continue;
                }
            }
            Field.Store store = entry.getValue().getStored() == 0 ? Field.Store.NO : Field.Store.YES;
            IndexableField textField = null;
            switch (entry.getValue().getFieldType()) {
                case 1:
                    textField = new TextField(entry.getKey(), val.toString(), store);
                    break;
                case 2:
                    textField = new StringField(entry.getKey(), val.toString(), store);
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