package com.hzq.researchtest.test.analyzer;

import com.bird.segment.extend.BirdExtendAnalyzer;
import com.bird.segment.extend.ExtendType;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 21:40
 */
public class MyIkAnalyzer {

    public static void main(String[] args) throws Exception {

        IKAnalyzer ikAnalyzer = new IKAnalyzer(true);


        String arr[] = {"我是中国人","同义词数量爆棚"};

        for(int i = 0; i< arr.length; i++) {
            TokenStream tokenStream = ikAnalyzer.tokenStream("hzq", new StringReader(arr[i]) );
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();//必须
            while(tokenStream.incrementToken()) {
                System.out.println(termAtt.toString());
            }
            System.out.println("===============================");
            tokenStream.close();//必须
        }
        ikAnalyzer.close();
    }
}
