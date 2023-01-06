package com.hzq.search.analyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/22 10:56
 */
public class MyJianpinAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String s) {
        MyJianpinTokenizer myJianpinTokenizer = new MyJianpinTokenizer();
        return new TokenStreamComponents(myJianpinTokenizer);
    }

    public static void main(String[] args) throws IOException {
        MyJianpinAnalyzer analyzer = new MyJianpinAnalyzer();
        String arr[] = {"兰州金鹏通讯工程有限责任公司青岛分公司", "青岛亚太物资有限公司"};

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
