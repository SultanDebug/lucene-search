package com.hzq.researchtest.test.analyzer;

import com.hzq.researchtest.util.PinyinUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;

/**
 * 中文  拼音合并分词
 *
 * @author Huangzq
 * @description
 * @date 2022/11/22 11:01
 */
public class MyPinyinTokenFilter extends TokenFilter {
    private CharTermAttribute termAtt;
    private PositionIncrementAttribute posIncrAtt;
    private AttributeSource.State current;
    private int skippedPositions;
    private String term;
    private String prefix = "";
    /**
     * Term最小长度，小于这个长度的不进行拼音转换
     **/
    private int minTermLength;
    private static final int DEFAULT_MIN_TERM_LENGTH = 1;

    protected MyPinyinTokenFilter(TokenStream input, int minTermLength) {
        super(input);
        this.termAtt = addAttribute(CharTermAttribute.class);
        this.posIncrAtt = addAttribute(PositionIncrementAttribute.class);
        this.minTermLength = minTermLength;
        if (this.minTermLength < DEFAULT_MIN_TERM_LENGTH) {
            this.minTermLength = DEFAULT_MIN_TERM_LENGTH;
        }
    }

    @Override
    public boolean incrementToken() throws IOException {
        term = termAtt.toString();
        if (StringUtils.isNotBlank(prefix) && term.length() >= minTermLength) {
            /*current = captureState();
            restoreState(current);*/
            String pinyinTerm = PinyinUtil.termToPinyin(term);
            if (StringUtils.isEmpty(pinyinTerm)) {
                prefix = null;
                return false;
            }
            termAtt.setEmpty();
            termAtt.append(pinyinTerm);
            posIncrAtt.setPositionIncrement(0);
            prefix = null;
            return true;
        }
        if (input.incrementToken()) {
            prefix = termAtt.toString();
            return true;
        } else {
            return false;
        }
    }

    /*@Override
    public boolean incrementToken() throws IOException {
        skippedPositions = 0;
        while (input.incrementToken()) {
            term = termAtt.toString();
            if (term.length() >= minTermLength) {
                String pinyinTerm = PinyinUtil.termToPinyin(term);
                termAtt.copyBuffer(pinyinTerm.toCharArray(), 0, pinyinTerm.length());
                if (skippedPositions != 0) {
                    posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions);
                }
                return true;
            }
            skippedPositions += posIncrAtt.getPositionIncrement();
        }
        return false;
    }*/
}
