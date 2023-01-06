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
 * ik分词，附加term标准化【简繁转换，全半转换，大小写，特殊字符】
 *
 * @author Huangzq
 * @date 2022/11/22 11:01
 */
public class MyIkTokenFilter extends TokenFilter {
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

    protected MyIkTokenFilter(TokenStream input, int minTermLength) {
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
        while (input.incrementToken()) {
            term = termAtt.toString();
            if (term.length() >= minTermLength) {
                //todo normal
                String nomalTerm = StringTools.normalServerString(term);
                if (StringUtils.isEmpty(nomalTerm)) {
                    continue;
                }
                termAtt.setEmpty();
                termAtt.append(nomalTerm);
                //termAtt.copyBuffer(nomalTerm.toCharArray(), 0, nomalTerm.length());
                posIncrAtt.setPositionIncrement(1);
                return true;
            }
        }
        return false;
    }
}
