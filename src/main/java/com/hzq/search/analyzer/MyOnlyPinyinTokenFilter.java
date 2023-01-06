package com.hzq.search.analyzer;

import com.hzq.search.util.PinyinUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * ik拼音合并分词
 *
 * @author Huangzq
 * @description
 * @date 2022/11/22 11:01
 */
public class MyOnlyPinyinTokenFilter extends TokenFilter {
    private CharTermAttribute termAtt;
    private PositionIncrementAttribute posIncrAtt;

    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private int skippedPositions;
    private String term;
    /**
     * Term最小长度，小于这个长度的不进行拼音转换
     **/
    private int minTermLength;

    private String prefix;
    private static final int DEFAULT_MIN_TERM_LENGTH = 1;

    protected MyOnlyPinyinTokenFilter(TokenStream input, int minTermLength) {
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
        /*skippedPositions = 0;
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
        }*/

        term = termAtt.toString();
        if (StringUtils.isNotEmpty(prefix) && term.length() >= minTermLength) {
            while (true) {
                if (StringUtils.isEmpty(prefix)) {
                    return false;
                }
                char c = prefix.charAt(0);
                prefix = prefix.substring(1);
                String pinyinTerm = PinyinUtil.termToPinyin(String.valueOf(c));
                if (StringUtils.isEmpty(pinyinTerm)) {
                    continue;
                }
                termAtt.setEmpty();
                termAtt.append(pinyinTerm);
                posIncrAtt.setPositionIncrement(1);
                return true;
            }
        }
        if (input.incrementToken()) {
            skippedPositions = 0;
            term = termAtt.toString();
            if (term.length() > 1) {
                prefix = term;
            }
            if (term.length() >= minTermLength) {
                String pinyinTerm = PinyinUtil.termToPinyin(term);
                termAtt.copyBuffer(pinyinTerm.toCharArray(), 0, pinyinTerm.length());
                posIncrAtt.setPositionIncrement(1);
                return true;
            }
            return true;
        } else {
            return false;
        }
    }
}
