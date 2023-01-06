package com.hzq.researchtest.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.io.StringReader;

/**
 * 归一化分词器
 * whiteSpaceFlag：true-去除特殊字符占位字符   false-不去除
 *
 * @author Huangzq
 * @date 2022/11/22 10:56
 */
@Slf4j
public class MyNormalAnalyzer extends Analyzer {
    boolean whiteSpaceFlag = false;

    public MyNormalAnalyzer(boolean whiteSpaceFlag) {
        this.whiteSpaceFlag = whiteSpaceFlag;
    }

    @Override
    protected TokenStreamComponents createComponents(String s) {
        MyNormalTokenizer tokenizer = new MyNormalTokenizer(whiteSpaceFlag);
        return new TokenStreamComponents(tokenizer);
    }

    public static void main(String[] args) throws IOException {
        MyNormalAnalyzer analyzer = new MyNormalAnalyzer(false);
        String arr[] = {"Abc@$測試", "aBC%*測試"};
        System.out.println((int) ';');
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
