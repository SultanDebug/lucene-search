package com.hzq.researchtest.analyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.io.StringReader;

/**
 * 特殊符号分词
 *
 * @author Huangzq
 * @date 2022/11/22 10:56
 */
public class MySpecialCharAnalyzer extends Analyzer {
    private char flag = ';';

    public MySpecialCharAnalyzer(char flag) {
        this.flag = flag;
    }

    @Override
    protected TokenStreamComponents createComponents(String s) {
        MySpecialCharTokenizer tokenizer = new MySpecialCharTokenizer(flag);
        MySpecialCharTokenFilter filter = new MySpecialCharTokenFilter(tokenizer, 1);
        return new TokenStreamComponents(tokenizer, filter);
    }

    public static void main(String[] args) throws IOException {
        MySpecialCharAnalyzer analyzer = new MySpecialCharAnalyzer(';');
        String arr[] = {"阿萨德;个梵蒂冈;鬼 地方个;aBC%*測試"};

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
    }
}
