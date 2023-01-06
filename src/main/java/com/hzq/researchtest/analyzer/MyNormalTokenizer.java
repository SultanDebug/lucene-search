package com.hzq.researchtest.analyzer;

import com.bird.search.util.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * 归一化分词器
 * whiteSpaceFlag：true-去除特殊字符占位字符   false-不去除
 *
 * @author Huangzq
 * @date 2022/11/17 21:40
 */
@Slf4j
public class MyNormalTokenizer extends Tokenizer {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    private String term = "";

    private boolean whiteSpaceFlag = false;
    ;

    private boolean flag = false;

    public MyNormalTokenizer(boolean whiteSpaceFlag) {
        this.whiteSpaceFlag = whiteSpaceFlag;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (flag) {
            return false;
        }
        int c = -1;
        while ((c = input.read()) != -1) {
            term += String.valueOf((char) c);
        }
        flag = true;
        String normalStr = whiteSpaceFlag ? StringTools.normalWithNotWordStr(term) : StringTools.normalStr(term);
        if (StringUtils.isEmpty(normalStr)) {
            termAtt.copyBuffer(new char[]{}, 0, 0);
            termAtt.resizeBuffer(0);
            termAtt.setLength(0);
            posIncrAtt.setPositionIncrement(1);
            offsetAtt.setOffset(0, 0);
            return true;
        }
        termAtt.copyBuffer(normalStr.toCharArray(), 0, normalStr.length());
        termAtt.resizeBuffer(normalStr.length());
        termAtt.setLength(normalStr.length());
        posIncrAtt.setPositionIncrement(1);
        offsetAtt.setOffset(0, normalStr.length());
        return true;
    }

    @Override
    public void reset() throws IOException {
        term = "";
        flag = false;
        termAtt.setEmpty();
        super.reset();
    }
}
