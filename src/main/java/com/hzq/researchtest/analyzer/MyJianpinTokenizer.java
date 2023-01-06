package com.hzq.researchtest.analyzer;

import com.bird.search.util.PinyinUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * @author Huangzq
 * @date 2022/11/17 21:40
 */
@Slf4j
public class MyJianpinTokenizer extends Tokenizer {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    private String term = "";

    private boolean flag = false;

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
        String jianpin = PinyinUtil.termToJianpin(term);
        if (StringUtils.isEmpty(jianpin)) {
            termAtt.copyBuffer(new char[]{}, 0, 0);
            termAtt.resizeBuffer(0);
            termAtt.setLength(0);
            posIncrAtt.setPositionIncrement(1);
            offsetAtt.setOffset(0, 0);
            return true;
        }
        termAtt.copyBuffer(jianpin.toCharArray(), 0, jianpin.length());
        termAtt.resizeBuffer(jianpin.length());
        termAtt.setLength(jianpin.length());
        posIncrAtt.setPositionIncrement(1);
        offsetAtt.setOffset(0, jianpin.length());
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
