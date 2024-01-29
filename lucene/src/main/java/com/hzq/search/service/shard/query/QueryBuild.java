package com.hzq.search.service.shard.query;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hzq.search.analyzer.MyCnPinyinAnalyzer;
import com.hzq.search.analyzer.MySingleCharAnalyzer;
import com.hzq.search.config.FieldDef;
import com.hzq.search.enums.FieldTypeEnum;
import com.hzq.search.util.PinyinUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.xml.builders.TermsQueryBuilder;
import org.apache.lucene.search.*;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Huangzq
 * @date 2022/12/15 16:28
 */
@Slf4j
public class QueryBuild {
    private static Analyzer ikAnalyzer = new IKAnalyzer(true);

    /**
     * name,used_name,product_brand_names,brand_names_algo,app_name,stock_name_short_array,stock_code_new_array,oper_name,credit_no
     */
    /**
     * Description:
     * 建议词第一版
     * 逻辑：字段精确term查询、前缀查询以及短语查询
     * 问题：纯粹查询，无法区分哪个字段命中
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/1/16 17:29
     */
    public static Query sugQueryV1(IndexSearcher searcher, String query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        builder.add(chinessQuery("name", query), BooleanClause.Occur.SHOULD);
        builder.add(chinessQuery("used_name", query), BooleanClause.Occur.SHOULD);
        builder.add(termPreQuery("jianpin", query), BooleanClause.Occur.SHOULD);
        builder.add(termPreQuery("oper_name", query), BooleanClause.Occur.SHOULD);
        builder.add(termPreQuery("pinyin", query), BooleanClause.Occur.SHOULD);
        builder.add(termPreQuery("credit_no", query), BooleanClause.Occur.SHOULD);

        builder.add(chinessQuery("product_brand_names", query), BooleanClause.Occur.SHOULD);
        builder.add(chinessQuery("brand_names_algo", query), BooleanClause.Occur.SHOULD);
        builder.add(chinessQuery("app_name", query), BooleanClause.Occur.SHOULD);
        builder.add(chinessQuery("stock_name_short_array", query), BooleanClause.Occur.SHOULD);
//        builder.add(chinessQuery("stock_code_new_array",query), BooleanClause.Occur.SHOULD);
        builder.add(chinessQuery("oper_name", query), BooleanClause.Occur.SHOULD);
        return builder.build();
    }

    /**
     * Description:
     * 建议词第二版
     * 逻辑：精确term查询和前缀查询，用布尔相关性配合加权查询及劝最大查询，区分字段命中信息
     * 问题：相关性排序无保障
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/1/16 17:30
     */
    public static Query sugQueryV2(String query) {
        List<Query> cos = new ArrayList<>();

        cos.add(new BoostQuery(new PrefixQuery(new Term("fuzz_name", query)), 20));
        Query nameQuery = allTermsAndQuery("name", query);
        if (nameQuery != null) {
            cos.add(new BoostQuery(nameQuery, 19));
        }

        cos.add(new BoostQuery(new PrefixQuery(new Term("fuzz_used_name", query)), 18));
        Query usedNameQuery = allTermsAndQuery("used_name", query);
        if (usedNameQuery != null) {
            cos.add(new BoostQuery(usedNameQuery, 17));
        }

        cos.add(new BoostQuery(termPreBoostQuery("oper_name_one", query), 16));
        cos.add(new BoostQuery(termPreBoostQuery("jianpin", query), 14));
        cos.add(new BoostQuery(termPreBoostQuery("pinyin", query), 12));
        cos.add(new BoostQuery(termPreBoostQuery("credit_no", query), 1));

        cos.add(new BoostQuery(new TermQuery(new Term("product_brand_names", query)), 10));
        cos.add(new BoostQuery(new TermQuery(new Term("brand_names_algo", query)), 2));
        cos.add(new BoostQuery(new TermQuery(new Term("app_name", query)), 4));
        cos.add(new BoostQuery(new TermQuery(new Term("stock_name_short_array", query)), 6));
        cos.add(new BoostQuery(new TermQuery(new Term("stock_code_new_array", query)), 8));

        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos, 0);

        /*BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(queries, BooleanClause.Occur.MUST);
        builder.add(IntPoint.newRangeQuery("found_years",1,3), BooleanClause.Occur.FILTER);*/

        return queries;
    }

    /**
     * 单字符模糊查询
     * 问题：
     * 1.英文单字符
     * 2.瑞浦贸易（上海）有限公司
     * 3.江苏司生建设有限公司 单字重复
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/1/13 16:48
     */
    public static Query singleWordQuery(String query, String filter, Map<String, FieldDef> fieldMap) {
        List<String> singleWordToken = getSingleWordToken(query);

        BooleanQuery.Builder nameWordQuery = new BooleanQuery.Builder();
        BooleanQuery.Builder userNameWordQuery = new BooleanQuery.Builder();

        int queryLength = singleWordToken.size();
        int minLength = 0;
        if (queryLength <= 5) {
            minLength = queryLength;
        } else if (queryLength <= 8) {
            minLength = queryLength - 2;
        } else {
            minLength = (int) Math.round(singleWordToken.size() * 0.8);
        }

        nameWordQuery.setMinimumNumberShouldMatch(minLength);
        userNameWordQuery.setMinimumNumberShouldMatch(minLength);

        for (String token : singleWordToken) {
            TermQuery termNameQuery = new TermQuery(new Term("single_fuzz_name", token));
            nameWordQuery.add(termNameQuery, BooleanClause.Occur.SHOULD);
            TermQuery termUserNameQuery = new TermQuery(new Term("single_fuzz_used_name", token));
            userNameWordQuery.add(termUserNameQuery, BooleanClause.Occur.SHOULD);
        }

        //条件过滤
        Query conditionFilter = StringUtils.isEmpty(filter) ? null : filterHandler(filter, fieldMap);

        //多字段匹配取最高得分
        List<Query> list = new ArrayList<>();
        list.add(nameWordQuery.build());
        list.add(userNameWordQuery.build());
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(list, 0);

        if (conditionFilter == null) {
            return queries;
        } else {
            BooleanQuery.Builder finalQuery = new BooleanQuery.Builder();
            finalQuery.add(queries, BooleanClause.Occur.MUST);
            finalQuery.add(conditionFilter, BooleanClause.Occur.FILTER);
            return finalQuery.build();
        }
    }


    /**
     * 拼音模糊查询
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/2/14 16:46
     */
    public static Query singleWordPyQuery(String query, String filter, Map<String, FieldDef> fieldMap) {
        List<String> singleWordToken = getSingleWordPyToken(query);

        BooleanQuery.Builder nameWordQuery = new BooleanQuery.Builder();
        BooleanQuery.Builder userNameWordQuery = new BooleanQuery.Builder();

        int queryLength = singleWordToken.size();
        int minLength = 0;
        if (queryLength <= 5) {
            minLength = queryLength;
        } else if (queryLength <= 8) {
            minLength = queryLength - 2;
        } else {
            minLength = (int) Math.round(singleWordToken.size() * 0.8);
        }

        nameWordQuery.setMinimumNumberShouldMatch(minLength);
        userNameWordQuery.setMinimumNumberShouldMatch(minLength);

        for (String token : singleWordToken) {
            TermQuery termNameQuery = new TermQuery(new Term("name_pinyin", token));
            nameWordQuery.add(termNameQuery, BooleanClause.Occur.SHOULD);
            TermQuery termUserNameQuery = new TermQuery(new Term("used_name_pinyin", token));
            userNameWordQuery.add(termUserNameQuery, BooleanClause.Occur.SHOULD);
        }

        //条件过滤
        Query conditionFilter = StringUtils.isEmpty(filter) ? null : filterHandler(filter, fieldMap);

        //多字段匹配取最高得分
        List<Query> list = new ArrayList<>();
        list.add(nameWordQuery.build());
        list.add(userNameWordQuery.build());
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(list, 0);

        if (conditionFilter == null) {
            return queries;
        } else {
            BooleanQuery.Builder finalQuery = new BooleanQuery.Builder();
            finalQuery.add(queries, BooleanClause.Occur.MUST);
            finalQuery.add(conditionFilter, BooleanClause.Occur.FILTER);
            return finalQuery.build();
        }
    }


    /**
     * 拼音模糊复合查询
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/2/14 16:46
     */
    public static Query singleWordComplexQuery(String query, String filter, Map<String, FieldDef> fieldMap) {
        List<String> singleWordToken = getSingleWordComplexToken(query);
        TermsQueryBuilder
        BooleanQuery.Builder nameWordQuery = new BooleanQuery.Builder();
        BooleanQuery.Builder userNameWordQuery = new BooleanQuery.Builder();
        int queryLength = singleWordToken.size();
        int minLength = 0;
        if (queryLength <= 5) {
            minLength = queryLength;
        } else {
            minLength = queryLength - 1;
        }
        nameWordQuery.setMinimumNumberShouldMatch(minLength);
        userNameWordQuery.setMinimumNumberShouldMatch(minLength);

        PhraseQuery.Builder namePhs = new PhraseQuery.Builder();
        PhraseQuery.Builder usedNamePhs = new PhraseQuery.Builder();
        namePhs.setSlop(1);
        usedNamePhs.setSlop(1);
        //int pos = 1;
        for (String token : singleWordToken) {
            String py = PinyinUtil.termToPinyin(token);
            namePhs.add(new Term("name_single_pinyin", py));
            usedNamePhs.add(new Term("used_name_single_pinyin", py));
            //pos++;


            TermQuery termNameQuery = new TermQuery(new Term("name_pinyin", token));
            nameWordQuery.add(termNameQuery, BooleanClause.Occur.SHOULD);
            TermQuery termUserNameQuery = new TermQuery(new Term("used_name_pinyin", token));
            userNameWordQuery.add(termUserNameQuery, BooleanClause.Occur.SHOULD);
        }

        //条件过滤
        Query conditionFilter = StringUtils.isEmpty(filter) ? null : filterHandler(filter, fieldMap);

        //多字段匹配取最高得分
        List<Query> pyList = new ArrayList<>();
        pyList.add(namePhs.build());
        pyList.add(usedNamePhs.build());
        DisjunctionMaxQuery pyQueries = new DisjunctionMaxQuery(pyList, 0);

        List<Query> termList = new ArrayList<>();
        termList.add(nameWordQuery.build());
        termList.add(userNameWordQuery.build());
        DisjunctionMaxQuery complexQueries = new DisjunctionMaxQuery(termList, 0);

        List<Query> complexList = new ArrayList<>();
        complexList.add(pyQueries);
        complexList.add(complexQueries);
        DisjunctionMaxQuery complex = new DisjunctionMaxQuery(complexList, 0);

        if (conditionFilter == null) {
            return complex;
        } else {
            BooleanQuery.Builder finalQuery = new BooleanQuery.Builder();
            finalQuery.add(complex, BooleanClause.Occur.MUST);
            finalQuery.add(conditionFilter, BooleanClause.Occur.FILTER);
            return finalQuery.build();
        }
    }

    public static Query filterHandler(String filter, Map<String, FieldDef> fieldMap) {
        BooleanQuery.Builder filterCondition = new BooleanQuery.Builder();

        JSONObject filterObj = JSON.parseObject(filter);
        AtomicBoolean keyFlag = new AtomicBoolean(false);

        filterObj.forEach((key, val) -> {
            if (fieldMap.containsKey(key)) {
                FieldDef fieldDef = fieldMap.get(key);
                if (val instanceof JSONArray) {
                    BooleanQuery.Builder builder = new BooleanQuery.Builder();
                    AtomicBoolean valFlag = new AtomicBoolean(false);
                    ((JSONArray) val).stream().forEach(o -> {
                        if (o instanceof JSONObject) {
                            JSONObject oo = (JSONObject) o;
                            Boolean left = oo.getBoolean("left");
                            Boolean right = oo.getBoolean("right");
                            if (FieldTypeEnum.INT_TYPE.getType().equals(fieldDef.getFieldType())) {
                                Integer min = oo.getInteger("min");
                                Integer max = oo.getInteger("max");
                                if (min == null) {
                                    min = Integer.MIN_VALUE;
                                } else {
                                    min = Boolean.TRUE.equals(left) ? min : Math.addExact(min, 1);
                                }

                                if (max == null) {
                                    max = Integer.MAX_VALUE;
                                } else {
                                    max = Boolean.TRUE.equals(right) ? max : Math.addExact(max, -1);
                                }

                                builder.add(IntPoint.newRangeQuery(key, min, max), BooleanClause.Occur.SHOULD);
                                valFlag.set(true);
                            } else if (FieldTypeEnum.DOUBLE_TYPE.getType().equals(fieldDef.getFieldType())) {
                                Double min = oo.getDouble("min");
                                Double max = oo.getDouble("max");
                                if (min == null) {
                                    min = Double.MIN_VALUE;
                                } else {
                                    min = Boolean.TRUE.equals(left) ? min : DoublePoint.nextUp(min);
                                }

                                if (max == null) {
                                    max = Double.MAX_VALUE;
                                } else {
                                    max = Boolean.TRUE.equals(right) ? max : DoublePoint.nextDown(max);
                                }

                                builder.add(DoublePoint.newRangeQuery(key, min, max), BooleanClause.Occur.SHOULD);
                                valFlag.set(true);
                            } else {
                                log.warn("数据{}类型{}暂未定义", o.getClass().getSimpleName(), fieldDef.getFieldType());
                            }

                        } else {
                            builder.add(new TermQuery(new Term(key, o.toString())), BooleanClause.Occur.SHOULD);
                            valFlag.set(true);
                        }
                    });
                    if (valFlag.get()) {
                        filterCondition.add(builder.build(), BooleanClause.Occur.MUST);
                        keyFlag.set(true);
                    }
                }
            } else {
                log.warn("字段{}未定义，请检查", key);
            }
        });

        return keyFlag.get() ? filterCondition.build() : null;
    }


    /**
     * 单字分词
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/2/14 16:45
     */
    private static List<String> getSingleWordToken(String query) {
        try (MySingleCharAnalyzer analyzer = new MySingleCharAnalyzer()) {
            List<String> res = new ArrayList<>();
            TokenStream tokenStream = analyzer.tokenStream("", query);
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                res.add(termAtt.toString());
            }
            tokenStream.close();//必须
            return res;
        } catch (Exception e) {
            log.error("模糊查询分词异常：{}", query, e);
        }
        return new ArrayList<>();
    }

    /**
     * 拼音分词结果
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/2/14 16:45
     */
    private static List<String> getSingleWordPyToken(String query) {
        try (MyCnPinyinAnalyzer analyzer = new MyCnPinyinAnalyzer(true)) {
            List<String> res = new ArrayList<>();
            TokenStream tokenStream = analyzer.tokenStream("", query);
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                res.add(termAtt.toString());
            }
            tokenStream.close();//必须
            return res;
        } catch (Exception e) {
            log.error("模糊查询分词异常：{}", query, e);
        }
        return new ArrayList<>();
    }

    /**
     * 拼音分词结果
     *
     * @param
     * @return
     * @author Huangzq
     * @date 2023/2/14 16:45
     */
    private static List<String> getSingleWordComplexToken(String query) {
        try (MySingleCharAnalyzer analyzer = new MySingleCharAnalyzer()) {
            List<String> res = new ArrayList<>();
            TokenStream tokenStream = analyzer.tokenStream("", query);
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                res.add(termAtt.toString());
            }
            tokenStream.close();//必须
            return res;
        } catch (Exception e) {
            log.error("模糊查询分词异常：{}", query, e);
        }
        return new ArrayList<>();
    }


    public static Query busidQuery(String query) {
        return new TermQuery(new Term("company_id", query));
    }

    public static Query booleanQuery(String query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        BoostQuery boostQuery1 = new BoostQuery(termPreQuery("fuzz_name", query), 30);
        BoostQuery boostQuery2 = new BoostQuery(termPreQuery("fuzz_used_name", query), 50);
        builder.add(boostQuery1, BooleanClause.Occur.SHOULD);
        builder.add(boostQuery2, BooleanClause.Occur.SHOULD);

        List<Query> cos = new ArrayList<>();
        cos.add(boostQuery1);
        cos.add(boostQuery2);
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos, 0);


        return queries;
//        return builder.build();
    }

    public static Query fuzzyQuery(String query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        Query nameQuery = termPreQuery("fuzz_name", query);
        Query usedNameQuery = termPreQuery("fuzz_used_name", query);
        FuzzyQuery fuzNameQuery = new FuzzyQuery(new Term("fuzz_name", query), 2, 3, 50, false);
        FuzzyQuery fuzUsedNameQuery = new FuzzyQuery(new Term("fuzz_used_name", query), 2, 3, 50, true);
        builder.add(nameQuery, BooleanClause.Occur.SHOULD);
        builder.add(usedNameQuery, BooleanClause.Occur.SHOULD);
        builder.add(fuzNameQuery, BooleanClause.Occur.SHOULD);
        builder.add(fuzUsedNameQuery, BooleanClause.Occur.SHOULD);
        return builder.build();
    }

    public static Query chinessQuery(String field, String query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        builder.add(prefixQuery(field, query), BooleanClause.Occur.SHOULD);
        builder.add(termQuery(field, query), BooleanClause.Occur.SHOULD);
        PhraseQuery phraseQuery = termsAndQuery(field, query);
        if (phraseQuery != null) {
            builder.add(phraseQuery, BooleanClause.Occur.SHOULD);
        }

        return builder.build();

    }

    public static Query textBoostQuery(String fieldId, String query) {
        List<Query> cos = new ArrayList<>();
        cos.add(new BoostQuery(new PrefixQuery(new Term(fieldId, query)), 2));
        cos.add(new BoostQuery(new TermQuery(new Term(fieldId, query)), 3));
        Query booleanQuery = allTermsAndQuery(fieldId, query);
        if (booleanQuery != null) {
            cos.add(new BoostQuery(booleanQuery, 1));
        }
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos, 0);
        return queries;
    }

    public static Query termPreQuery(String field, String query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(prefixQuery(field, query), BooleanClause.Occur.SHOULD);
        builder.add(termQuery(field, query), BooleanClause.Occur.SHOULD);
        return builder.build();

    }


    public static Query termPreBoostQuery(String fieldId, String query) {

        BoostQuery query1 = new BoostQuery(new PrefixQuery(new Term(fieldId, query)), 1);
        BoostQuery query2 = new BoostQuery(new TermQuery(new Term(fieldId, query)), 2);

        List<Query> cos = new ArrayList<>();
        cos.add(query1);
        cos.add(query2);
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos, 0);

        return queries;

    }


    private static TermQuery termQuery(String fieldId, String query) {
        return new TermQuery(new Term(fieldId, query));
    }

    private static PrefixQuery prefixQuery(String fieldId, String query) {
        return new PrefixQuery(new Term(fieldId, query));
    }

    private static Query allTermsAndQuery(String fieldId, String query) {
        try {
            TokenStream tokenStream = ikAnalyzer.tokenStream(fieldId, query);
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();//必须
            List<Pair<String, Integer>> terms = new ArrayList<>();
            while (tokenStream.incrementToken()) {
                terms.add(Pair.of(termAtt.toString(), offsetAttribute.startOffset()));
            }
            tokenStream.close();//必须


            QueryParser queryParser = new QueryParser(fieldId, new WhitespaceAnalyzer());
            queryParser.setDefaultOperator(QueryParser.Operator.AND);
            StringBuilder q = new StringBuilder();
            for (Pair<String, Integer> term : terms) {
                q.append(" ").append(term.getLeft());
            }

            return queryParser.parse(fieldId + ":" + q.toString().trim());
        } catch (Exception e) {
            log.error("分词异常{}", query, e);
        }
        return null;
    }

    private static PhraseQuery termsAndQuery(String fieldId, String query) {
        try {
            TokenStream tokenStream = ikAnalyzer.tokenStream(fieldId, query);
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();//必须
            List<Pair<String, Integer>> terms = new ArrayList<>();
            while (tokenStream.incrementToken()) {
                terms.add(Pair.of(termAtt.toString(), offsetAttribute.startOffset()));
            }
            tokenStream.close();//必须


            PhraseQuery.Builder builder = new PhraseQuery.Builder();
            for (Pair<String, Integer> term : terms) {
                builder.add(new Term(fieldId, term.getLeft()));
            }
            builder.setSlop(3);
            return builder.build();
        } catch (Exception e) {
            log.error("分词异常{}", query, e);
        }
        return null;
    }

}
