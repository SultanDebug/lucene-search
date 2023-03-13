package com.hzq.search.service.shard;

import com.hzq.search.analyzer.*;
import com.hzq.search.config.FieldDef;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.util.CollectionUtils;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * 索引管理
 * 1.初始化
 * 2.新增、修改
 * 3.主索引、增量索引合并
 *
 * @author Huangzq
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
        this.fsPath = fsPath + File.separator + shardNum + File.separator + fsPathName;
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
        PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new MyIkAnalyzer(false), fieldAnalyzers);

        IndexWriterConfig conf = new IndexWriterConfig(analyzer);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return conf;
    }

    public static void main(String[] args) throws IOException {
        Analyzer analyzer = new StandardAnalyzer();
        Directory index = new RAMDirectory();

        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("name", new IKAnalyzer(false));
        fieldAnalyzers.put("id", new StandardAnalyzer());
        fieldAnalyzers.put("used_name", new MyCnPinyinAnalyzer(true));
        PerFieldAnalyzerWrapper analyzerWrapper = new PerFieldAnalyzerWrapper(analyzer, fieldAnalyzers);
        Similarity similarity = new PerFieldSimilarityWrapper() {
            @Override
            public Similarity get(String name) {
                if (name.equals("name")) {
                    return new BM25Similarity();
                } else if (name.equals("id")) {
                    return new BooleanSimilarity();
                }
                return new BM25Similarity();
            }
        };
        BytesRef bytesRef = analyzerWrapper.normalize("id", "ABCD-123");
        System.out.println(bytesRef.utf8ToString());
        IndexWriterConfig conf = new IndexWriterConfig(analyzerWrapper);
        conf.setSimilarity(similarity);
        IndexWriter writer = new IndexWriter(index, conf);

        Document doc1 = new Document();
        doc1.add(new StringField("id", analyzerWrapper.normalize("id", "ABCD123").utf8ToString(), Field.Store.YES));
        doc1.add(new TextField("name", "高新技术", Field.Store.YES));
//        FieldType fieldType = new FieldType();
//        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
//        fieldType.setStored(true);
//        doc1.add(new Field("used_name", "内蒙古京新药业有限公司", fieldType));
        doc1.add(new TextField("used_name", "内蒙古京新药业有限公司", Field.Store.YES));

        writer.addDocument(doc1);
        writer.commit();
        writer.close();

        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        //按域获取分词结果
        List<String> idSegments = getSegmentToken("id", "ABCD-123", analyzerWrapper);
        List<String> nameSegments = getSegmentToken("name", "高新技术", analyzerWrapper);
        List<String> usedNameSegments = getSegmentToken("used_name", "内蒙古京新药业有限公司", analyzerWrapper);
        System.out.println(idSegments);
        System.out.println(nameSegments);
        System.out.println(usedNameSegments);
//        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
//        Query query = new TermQuery(new Term("id", analyzerWrapper.normalize("id", "AbCD123").utf8ToString()));
//        Query query2 = new TermQuery(new Term("name", "高新"));
//        booleanQuery.add(query, BooleanClause.Occur.SHOULD);
//        booleanQuery.add(query2, BooleanClause.Occur.SHOULD);
//        TopDocs docs = searcher.search(booleanQuery, 10);

        Query phraseQuery = new PhraseQuery.Builder()
                .add(new Term("used_name", "内蒙古"))
                .add(new Term("used_name", "新"))
                .setSlop(1).build();

        TopDocs docs = searcher.search(phraseQuery, 10);

        ScoreDoc[] hits = docs.scoreDocs;
        for (int i = 0; i < hits.length; i++) {
            ScoreDoc scoreDoc = hits[i];
            // 输出满足查询条件的 文档号
            Document doc = searcher.doc(scoreDoc.doc);
            System.out.println(doc);
        }
    }

    /**
     * 按域获取分词结果
     *
     * @param query
     * @return
     */
    private static List<String> getSegmentToken(String field, String query, PerFieldAnalyzerWrapper analyzerWrapper) {
        TokenStream tokenStream = analyzerWrapper.tokenStream(field, query);
        List<String> res = new ArrayList<>();
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        try {
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                res.add(termAtt.toString());
            }
            tokenStream.close();//必须
        } catch (IOException e) {
            log.error("文档域:{}, 分词内容:{}, 分词异常：{}", field, query, e);
        }

        return res;
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

            //todo 待完善
            mainIndex.forceMerge(1);
            mainIndex.flush();
            mainIndex.commit();

            //通知load端重新加载
            shardIndexLoadService.indexUpdate(null);
            log.info("分片【" + shardNum + "】合并数量：{},花费：{}", merge, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引合并失败：{}", e.getMessage(), e);
        } finally {
            try {
                if (mainIndex != null) {
                    mainIndex.close();
                }
                if (fsDirectory != null) {
                    fsDirectory.close();
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
        Directory fsDirectory = null;
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
            shardIndexLoadService.indexUpdate(null);

            log.info("索引更新结束：{}", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("索引初始化失败", e);
        } finally {
            try {
                if (mainIndex != null) {
                    mainIndex.close();
                }
                if (fsDirectory != null) {
                    fsDirectory.close();
                }
            } catch (Exception e) {
                log.error("更新索引关闭失败：{}", e.getMessage(), e);
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
    public void noticeSearcher(String path) {
        try {
            if (mainIndex != null) {

                //mainIndex.flush();
                mainIndex.forceMerge(1);
                mainIndex.commit();
                mainIndex.close();
            }
            if (fsDirectory != null) {
                fsDirectory.close();
            }
        } catch (Exception e) {
            log.error("索引删除关闭失败：{}", e.getMessage(), e);
        }
        shardIndexLoadService.indexUpdate(path);
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
    public void flushIndex() {
        try {
            if (mainIndex != null) {
                mainIndex.flush();
            }
        } catch (Exception e) {
            log.error("索引刷盘失败：{}", e.getMessage(), e);
        }
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
                    analyzer = new MyIkAnalyzer(false);
                    break;
                case 2:
                    // 拼音字段使用标准分词器
//                    analyzer = new MyCnPinyinAnalyzer(true);
                    analyzer = new MyNGramAnalyzer();
                    break;
                case 3:
                    // 简拼字段使用标准分词器
                    analyzer = new MyJianpinAnalyzer();
                    break;
                case 4:
                    // 全拼字段使用标准分词器
                    analyzer = new MyAllPinyinAnalyzer();
                    break;
                case 5:
                    // 特殊字符分词器
                    analyzer = new MySpecialCharAnalyzer(entry.getValue().getSpecialChar(), entry.getValue().getSpecialNormalFlag());
                    break;
                case 6:
                    // 归一化不替换特殊字符分词器
                    analyzer = new MyNormalAnalyzer(false);
                    break;
                case 7:
                    // 归一化替换特殊字符分词器
                    analyzer = new MyNormalAnalyzer(true);
                    break;
                case 8:
                    // 单字归一化分词器
                    analyzer = new MySingleCharAnalyzer();
                    break;
                case 9:
                    // 单字拼音分词
                    analyzer = new MySingleCharPyAnalyzer();
                    break;
                default:
                    // 默认使用ik归一化分词器
                    analyzer = new MyIkAnalyzer(false);
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
                val = valMap.get(entry.getValue().getParentField());
                if (Objects.isNull(val)) {
                    continue;
                }
            }
            String fieldVal = val.toString();
            /*String fieldVal = StringTools.normalServerString(val.toString());
            if(entry.getValue().getFieldType() == 2 || entry.getValue().getFieldType() == 3){
                fieldVal = fieldVal.replaceAll(" ","");
            }*/
            Field.Store store = entry.getValue().getStored() == 0 ? Field.Store.NO : Field.Store.YES;
            IndexableField field = null;
            switch (entry.getValue().getFieldType()) {
                case 1:
                    field = new TextField(entry.getKey(), fieldVal, store);
                    break;
                case 2:
                    field = new StringField(entry.getKey(), fieldVal, store);
                    break;
                case 3:
                    field = new IntPoint(entry.getKey(), Integer.parseInt(fieldVal));
                    doc.add(new NumericDocValuesField(entry.getKey(), Integer.parseInt(fieldVal)));
                    if (store.equals(Field.Store.YES)) {
                        doc.add(new StoredField(entry.getKey(), Integer.parseInt(fieldVal)));
                    }
                    break;
                case 4:
                    field = new DoublePoint(entry.getKey(), Double.parseDouble(fieldVal));
                    doc.add(new DoubleDocValuesField(entry.getKey(), Double.parseDouble(fieldVal)));
                    if (store.equals(Field.Store.YES)) {
                        doc.add(new StoredField(entry.getKey(), Double.parseDouble(fieldVal)));
                    }
                    break;
                default:
                    break;
            }

            //文档标准化，特殊字符、大小写、简繁、全半等
            doc.add(field);
        }
        return doc;
    }
}
