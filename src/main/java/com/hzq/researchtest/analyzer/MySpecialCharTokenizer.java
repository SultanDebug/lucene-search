package com.hzq.researchtest.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.util.CharTokenizer;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/23 09:14
 */
@Slf4j
public class MySpecialCharTokenizer  extends CharTokenizer {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    private char flag = ' ';
    
    public MySpecialCharTokenizer(char flag){
        this.flag = flag;
    }

    @Override
    protected boolean isTokenChar(int i) {
        return flag != i;
    }
}
