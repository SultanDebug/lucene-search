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
 * 单字分词器，带归一化
 *
 * @author Huangzq
 * @date 2022/11/22 11:01
 */
public class MySingleCharTokenFilter extends TokenFilter {
    private CharTermAttribute termAtt;
    private PositionIncrementAttribute posIncrAtt;

    private OffsetAttribute offsetAtt;

    private String term;

    protected MySingleCharTokenFilter(TokenStream input) {
        super(input);
        this.termAtt = addAttribute(CharTermAttribute.class);
        this.posIncrAtt = addAttribute(PositionIncrementAttribute.class);
        this.offsetAtt = addAttribute(OffsetAttribute.class);
    }

    @Override
    public boolean incrementToken() throws IOException {
        while (input.incrementToken()) {
            term = termAtt.toString();
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
        return false;
    }
}
