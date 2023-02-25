package com.hzq.search.service.shard.query;

import com.hzq.search.analyzer.MyCnPinyinAnalyzer;
import com.hzq.search.analyzer.MySingleCharAnalyzer;
import com.hzq.search.config.FieldDef;
import com.hzq.search.enums.QueryTypeEnum;
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
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hzq.search.enums.QueryTypeEnum.*;

/**
 * @author Huangzq
 * @date 2022/12/15 16:28
 */
@Slf4j
@Component
public class EnterpriseQueryBuild extends QueryBuildAbstract implements InitializingBean {
    private static Analyzer ikAnalyzer = new IKAnalyzer(true);

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


    public static Query pinyinPhraseQuery(String query, String filter, Map<String, FieldDef> fieldMap) {
        List<String> singleWordToken = getSingleWordComplexToken(query);

        PhraseQuery.Builder namePhs = new PhraseQuery.Builder();
        PhraseQuery.Builder usedNamePhs = new PhraseQuery.Builder();
        namePhs.setSlop(1);
        usedNamePhs.setSlop(1);
        for (String token : singleWordToken) {
            String py = PinyinUtil.termToPinyin(token);
            namePhs.add(new Term("name_single_pinyin", py));
            usedNamePhs.add(new Term("used_name_single_pinyin", py));
        }

        //条件过滤
        Query conditionFilter = StringUtils.isEmpty(filter) ? null : filterHandler(filter, fieldMap);

        //多字段匹配取最高得分
        List<Query> pyList = new ArrayList<>();
        pyList.add(namePhs.build());
        pyList.add(usedNamePhs.build());
        DisjunctionMaxQuery pyQueries = new DisjunctionMaxQuery(pyList, 0);

        if (conditionFilter == null) {
            return pyQueries;
        } else {
            BooleanQuery.Builder finalQuery = new BooleanQuery.Builder();
            finalQuery.add(pyQueries, BooleanClause.Occur.MUST);
            finalQuery.add(conditionFilter, BooleanClause.Occur.FILTER);
            return finalQuery.build();
        }
    }


    public static Query singleWordFuzzyQuery(String query, String filter, Map<String, FieldDef> fieldMap) {
        List<String> singleWordToken = getSingleWordComplexToken(query);

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

        for (String token : singleWordToken) {
            TermQuery termNameQuery = new TermQuery(new Term("name_pinyin", token));
            nameWordQuery.add(termNameQuery, BooleanClause.Occur.SHOULD);
            TermQuery termUserNameQuery = new TermQuery(new Term("used_name_pinyin", token));
            userNameWordQuery.add(termUserNameQuery, BooleanClause.Occur.SHOULD);
        }

        //条件过滤
        Query conditionFilter = StringUtils.isEmpty(filter) ? null : filterHandler(filter, fieldMap);

        //多字段匹配取最高得分
        List<Query> termList = new ArrayList<>();
        termList.add(nameWordQuery.build());
        termList.add(userNameWordQuery.build());
        DisjunctionMaxQuery complexQueries = new DisjunctionMaxQuery(termList, 0);

        if (conditionFilter == null) {
            return complexQueries;
        } else {
            BooleanQuery.Builder finalQuery = new BooleanQuery.Builder();
            finalQuery.add(complexQueries, BooleanClause.Occur.MUST);
            finalQuery.add(conditionFilter, BooleanClause.Occur.FILTER);
            return finalQuery.build();
        }
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


    public static Query busIdQuery(String query) {
        return new TermQuery(new Term("company_id", query));
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

    @Override
    public Pair<String , Query> buildQuery(String query, String filter, Map<String, FieldDef> fieldMap, QueryTypeEnum type) {
        switch (type) {
            case COMPLEX_QUERY:
                return Pair.of(COMPLEX_QUERY.getType(),singleWordComplexQuery(query, filter, fieldMap));
            case SINGLE_FUZZY_QUERY:
                return Pair.of(SINGLE_FUZZY_QUERY.getType(),singleWordPyQuery(query, filter, fieldMap));
            case PREFIX_QUERY:
                return Pair.of(PREFIX_QUERY.getType(),sugQueryV2(query));
            case PINYIN_QUERY:
                return Pair.of(PINYIN_QUERY.getType(),pinyinPhraseQuery(query, filter, fieldMap));
            case FUZZY_QUERY:
                return Pair.of(FUZZY_QUERY.getType(),singleWordFuzzyQuery(query, filter, fieldMap));
            default:
                return Pair.of(DETAIL_BY_COMPANY_ID.getType(),busIdQuery(query));
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        QueryManager.setQueryMap("enterprise", this);
    }
}
