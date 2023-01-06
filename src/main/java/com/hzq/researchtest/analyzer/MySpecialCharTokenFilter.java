package com.hzq.researchtest.analyzer;

import com.bird.search.util.StringTools;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * 特殊符号分词，归一化
 *
 * @author Huangzq
 * @date 2022/11/22 11:01
 */
public class MySpecialCharTokenFilter extends TokenFilter {
    private CharTermAttribute termAtt;
    private PositionIncrementAttribute posIncrAtt;

    private OffsetAttribute offsetAtt;

    private String term;
    /**
     * Term最小长度，小于这个长度的不进行拼音转换
     **/
    private int minTermLength;

    private String prefix;
    private static final int DEFAULT_MIN_TERM_LENGTH = 1;

    protected MySpecialCharTokenFilter(TokenStream input, int minTermLength) {
        super(input);
        this.termAtt = addAttribute(CharTermAttribute.class);
        this.posIncrAtt = addAttribute(PositionIncrementAttribute.class);
        this.offsetAtt = addAttribute(OffsetAttribute.class);
        this.minTermLength = minTermLength;
        if (this.minTermLength < DEFAULT_MIN_TERM_LENGTH) {
            this.minTermLength = DEFAULT_MIN_TERM_LENGTH;
        }
    }

    @Override
    public boolean incrementToken() throws IOException {
        while (input.incrementToken()) {
            term = termAtt.toString();
            if (term.length() >= minTermLength) {
                //todo normal
                String nomalTerm = StringTools.normalWithNotWordStr(term);
                if (StringUtils.isEmpty(nomalTerm)) {
                    continue;
                }
                termAtt.setEmpty();
                termAtt.append(nomalTerm);
                posIncrAtt.setPositionIncrement(1);
                return true;
            }
        }
        return false;
    }
}