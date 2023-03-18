package com.hzq.search.analyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;
import org.wltea.analyzer.lucene.IKTokenizer;

import java.io.IOException;
import java.io.StringReader;

/**
 * ik分词，附加term标准化【简繁转换，全半转换，大小写，特殊字符】
 * @author Huangzq
 * @date 2022/11/22 10:56
 */
public class MyIkAnalyzer extends Analyzer {

    private boolean useSmart;

    public MyIkAnalyzer(boolean useSmart) {
        this.useSmart = useSmart;
    }


    @Override
    protected TokenStreamComponents createComponents(String s) {
        Tokenizer tokenizer = new IKTokenizer(useSmart);
        MyIkTokenFilter myIkTokenFilter = new MyIkTokenFilter(tokenizer, 1);
        return new TokenStreamComponents(tokenizer, myIkTokenFilter);
    }

    public static void main(String[] args) throws IOException {
        IKAnalyzer ikAnalyzer = new IKAnalyzer(true);
        String arr[] = {"维正[知]測試识产权科技有限公司"};

        for (int i = 0; i < arr.length; i++) {
            TokenStream tokenStream = ikAnalyzer.tokenStream("hzq", new StringReader(arr[i]));
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                System.out.println(termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
            }
            System.out.println("===============================");
            tokenStream.close();//必须
        }

        MyIkAnalyzer analyzer = new MyIkAnalyzer(true);
        String arr1[] = {"维正[知]測試识产权科技有限公司"};

        for (int i = 0; i < arr1.length; i++) {
            TokenStream tokenStream = analyzer.tokenStream("hzq", new StringReader(arr1[i]));
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                System.out.println(termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
            }
            System.out.println("===============================");
            tokenStream.close();//必须
        }



        KeywordAnalyzer keywordAnalyzer = new KeywordAnalyzer();
        String arr2[] = {"Aa98@#$%^&*()"};

        for (int i = 0; i < arr2.length; i++) {
            TokenStream tokenStream = keywordAnalyzer.tokenStream("hzq", new StringReader(arr2[i]));
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                System.out.println(termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
            }
            System.out.println("===============================");
            tokenStream.close();//必须
        }

    }
}
