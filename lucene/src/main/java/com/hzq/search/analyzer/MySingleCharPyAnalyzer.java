package com.hzq.search.analyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
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
public class MySingleCharPyAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String s) {
//        MySingleCharTokenizer tokenizer = new MySingleCharTokenizer();
        StandardTokenizer tokenizer = new StandardTokenizer();
        MySingleCharPyTokenFilter filter = new MySingleCharPyTokenFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, filter);
    }

    public static void main(String[] args) throws IOException {
        MySingleCharPyAnalyzer analyzer = new MySingleCharPyAnalyzer();
        String arr[] = {"中国邮政速递物流股份有限公司内蒙古自治区满洲里分公司商贸中心营业部","内蒙古京新药业有限公司"};

        for (int i = 0; i < arr.length; i++) {
            TokenStream tokenStream = analyzer.tokenStream("hzq", new StringReader(arr[i]));
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


        StandardAnalyzer analyzer1 = new StandardAnalyzer();
        String arr1[] = {"鬼 地方个aBC%*測試"};

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
