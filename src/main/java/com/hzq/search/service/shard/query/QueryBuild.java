package com.hzq.search.service.shard.query;

import com.hzq.search.util.StringTools;
import lombok.extern.slf4j.Slf4j;
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
import org.apache.lucene.search.*;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.util.ArrayList;
import java.util.List;

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

    public static Query sugQuery(String query){
        List<Query> cos = new ArrayList<>();

        cos.add(new BoostQuery(new PrefixQuery(new Term("fuzz_name", query)),20));
        Query nameQuery = allTermsAndQuery("name", query);
        if (nameQuery != null) {
            cos.add(new BoostQuery(nameQuery,19));
        }

        cos.add(new BoostQuery(new PrefixQuery(new Term("fuzz_used_name", query)),18));
        Query usedNameQuery = allTermsAndQuery("used_name", query);
        if (usedNameQuery != null) {
            cos.add(new BoostQuery(usedNameQuery,17));
        }

        cos.add(new BoostQuery(termPreBoostQuery("oper_name_one", query),16));
        cos.add(new BoostQuery(termPreBoostQuery("jianpin", query),14));
        cos.add(new BoostQuery(termPreBoostQuery("pinyin", query),12));
        cos.add(new BoostQuery(termPreBoostQuery("credit_no", query),1));

        cos.add(new BoostQuery(new TermQuery(new Term("product_brand_names", query)),10));
        cos.add(new BoostQuery(new TermQuery(new Term("brand_names_algo", query)),2));
        cos.add(new BoostQuery(new TermQuery(new Term("app_name", query)),4));
        cos.add(new BoostQuery(new TermQuery(new Term("stock_name_short_array", query)),6));
        cos.add(new BoostQuery(new TermQuery(new Term("stock_code_new_array", query)),8));

        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos,0);

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(queries, BooleanClause.Occur.MUST);
        builder.add(IntPoint.newRangeQuery("found_years",1,3), BooleanClause.Occur.MUST);

        return builder.build();
    }

    public static Query singleWordQuery(String query){

        BooleanQuery.Builder singleWordQuery = new BooleanQuery.Builder();
        String normalQuery = StringTools.normalWithNotWordStr(query);
        singleWordQuery.setMinimumNumberShouldMatch((int) Math.round(normalQuery.length()*0.8));

        for (char c : normalQuery.toCharArray()) {
            TermQuery termQuery = new TermQuery(new Term("single_fuzz_name",String.valueOf(c)));
            //ConstantScoreQuery scoreQuery = new ConstantScoreQuery(termQuery);
            //BoostQuery boostQuery = new BoostQuery(scoreQuery,1);
            singleWordQuery.add(termQuery, BooleanClause.Occur.SHOULD);
        }

        BooleanQuery.Builder filterCondition = new BooleanQuery.Builder();

        BooleanQuery.Builder yearsCondition = new BooleanQuery.Builder();
        yearsCondition.add(IntPoint.newRangeQuery("found_years",1,10), BooleanClause.Occur.SHOULD);
        yearsCondition.add(IntPoint.newRangeQuery("found_years",15,20), BooleanClause.Occur.SHOULD);

        BooleanQuery.Builder capiCondition = new BooleanQuery.Builder();
        capiCondition.add(DoublePoint.newRangeQuery("reg_capi",0,30), BooleanClause.Occur.SHOULD);
        capiCondition.add(DoublePoint.newRangeQuery("reg_capi",900,1000), BooleanClause.Occur.SHOULD);

        filterCondition.add(yearsCondition.build(),BooleanClause.Occur.MUST);
        filterCondition.add(capiCondition.build(),BooleanClause.Occur.MUST);

        BooleanQuery.Builder complexQuery = new BooleanQuery.Builder();
        complexQuery.add(singleWordQuery.build(), BooleanClause.Occur.MUST);
        complexQuery.add(filterCondition.build(), BooleanClause.Occur.FILTER);


        return complexQuery.build();
    }

    public static Query booleanQuery(String query){
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        BoostQuery boostQuery1 = new BoostQuery(termPreQuery("fuzz_name", query),30);
        BoostQuery boostQuery2 = new BoostQuery(termPreQuery("fuzz_used_name", query),50);
        builder.add(boostQuery1,BooleanClause.Occur.SHOULD);
        builder.add(boostQuery2,BooleanClause.Occur.SHOULD);

        List<Query> cos = new ArrayList<>();
        cos.add(boostQuery1);
        cos.add(boostQuery2);
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos,0);



        return queries;
//        return builder.build();
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

    public static Query textBoostQuery(String fieldId, String query) {
        List<Query> cos = new ArrayList<>();
        cos.add(new BoostQuery(new PrefixQuery(new Term(fieldId, query)),2));
        cos.add(new BoostQuery(new TermQuery(new Term(fieldId, query)),3));
        Query booleanQuery = allTermsAndQuery(fieldId, query);
        if (booleanQuery != null) {
            cos.add(new BoostQuery(booleanQuery,1));
        }
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos,0);
        return queries;
    }

    public static Query termPreQuery(String field, String query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(prefixQuery(field, query), BooleanClause.Occur.SHOULD);
        builder.add(termQuery(field, query), BooleanClause.Occur.SHOULD);
        return builder.build();

    }


    public static Query termPreBoostQuery(String fieldId, String query) {

        BoostQuery query1 = new BoostQuery(new PrefixQuery(new Term(fieldId, query)),1);
        BoostQuery query2 = new BoostQuery(new TermQuery(new Term(fieldId, query)),2);

        List<Query> cos = new ArrayList<>();
        cos.add(query1);
        cos.add(query2);
        DisjunctionMaxQuery queries = new DisjunctionMaxQuery(cos,0);

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


            QueryParser queryParser = new QueryParser(fieldId,new WhitespaceAnalyzer());
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
