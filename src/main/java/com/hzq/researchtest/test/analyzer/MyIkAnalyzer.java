package com.hzq.researchtest.test.analyzer;

import com.hzq.researchtest.util.PinyinUtil;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.StringReader;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 21:40
 */
public class MyIkAnalyzer {

    public static void main(String[] args) throws Exception {

        IKAnalyzer ikAnalyzer = new IKAnalyzer(true);


        String arr[] = {"我是中国人的大爷", "同义词数量爆棚"};

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

        for (int i = 0; i < arr.length; i++) {
            String s = PinyinUtil.termToPinyin(arr[i]);
            TokenStream tokenStream = ikAnalyzer.tokenStream("hzq", new StringReader(s));
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute positionIncrementAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                System.out.println(termAtt.toString() + ":" + offsetAttribute.startOffset() + "/" + offsetAttribute.endOffset() + "==>" + positionIncrementAttribute.getPositionIncrement());
            }
            System.out.println("================================");
            tokenStream.close();//必须
        }
        ikAnalyzer.close();
    }
}
