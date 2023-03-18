package com.hzq.search.analyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.io.StringReader;

/**
 * 单字分词器，带归一化
 *
 * @author Huangzq
 * @date 2022/11/22 10:56
 */
public class MyNGramAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String s) {
        MyNormalTokenizer tokenizer = new MyNormalTokenizer(true);
        NGramTokenFilter filter = new NGramTokenFilter(tokenizer,1,3,true);
        MyNGramTokenFilter filter1 = new MyNGramTokenFilter(filter);
        return new TokenStreamComponents(tokenizer, filter1);
    }

    public static void main(String[] args) throws IOException {
        MyNGramAnalyzer analyzer1 = new MyNGramAnalyzer();
        String arr1[] = {"维正企知道网络有限公司", "我是长沙ren", "CharTermAttribute  you xian gongsi"};

        for (int i = 0; i < arr1.length; i++) {
            TokenStream tokenStream = analyzer1.tokenStream("hzq", new StringReader(arr1[i]));
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                System.out.println(termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
            }
            System.out.println("结果信息：" + termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
            System.out.println("===============================");
            tokenStream.close();//必须
        }

    }
}
