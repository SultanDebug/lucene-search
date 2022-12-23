package com.hzq.researchtest.service.single.shard.query;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/15 16:28
 */
@Slf4j
public class QueryBuild {
    private static Analyzer ikAnalyzer = new IKAnalyzer(true);

    /**
     * name,used_name,product_brand_names,brand_names_algo,app_name,stock_name_short_array,stock_code_new_array,oper_name,credit_no
     */
    public static Query sugQuery(IndexSearcher searcher,String query) {
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

    public static Query booleanQuery(String query){
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        BoostQuery boostQuery1 = new BoostQuery(termPreQuery("fuzz_name", query),5);
        BoostQuery boostQuery2 = new BoostQuery(termPreQuery("fuzz_used_name", query),10);
        builder.add(boostQuery1,BooleanClause.Occur.SHOULD);
        builder.add(boostQuery2,BooleanClause.Occur.SHOULD);

        return builder.build();
    }

    public static Query fuzzyQuery(String query){
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        Query nameQuery = termPreQuery("fuzz_name", query);
        Query usedNameQuery = termPreQuery("fuzz_used_name", query);
        FuzzyQuery fuzNameQuery = new FuzzyQuery(new Term("fuzz_name",query),2,3,50,false);
        FuzzyQuery fuzUsedNameQuery = new FuzzyQuery(new Term("fuzz_used_name",query),2,3,50,true);
        builder.add(nameQuery,BooleanClause.Occur.SHOULD);
        builder.add(usedNameQuery,BooleanClause.Occur.SHOULD);
        builder.add(fuzNameQuery,BooleanClause.Occur.SHOULD);
        builder.add(fuzUsedNameQuery,BooleanClause.Occur.SHOULD);
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

    public static Query termPreQuery(String field, String query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(prefixQuery(field, query), BooleanClause.Occur.SHOULD);
        builder.add(termQuery(field, query), BooleanClause.Occur.SHOULD);
        return builder.build();

    }


    private static TermQuery termQuery(String fieldId, String query) {
        return new TermQuery(new Term(fieldId, query));
    }

    private static PrefixQuery prefixQuery(String fieldId, String query) {
        return new PrefixQuery(new Term(fieldId, query));
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
