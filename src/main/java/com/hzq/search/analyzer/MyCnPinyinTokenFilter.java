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
 * @date 2022/11/22 11:01
 */
public class MyCnPinyinTokenFilter extends TokenFilter {
    private CharTermAttribute termAtt;
    private PositionIncrementAttribute posIncrAtt;

    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    //private int skippedPositions;
    private String term;
    /**
     * Term最小长度，小于这个长度的不进行拼音转换
     **/
    private int minTermLength;

    private String pyTerm;

    private String cnTerm;
    private static final int DEFAULT_MIN_TERM_LENGTH = 1;

    protected MyCnPinyinTokenFilter(TokenStream input, int minTermLength) {
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
        if(StringUtils.isNotEmpty(cnTerm)){
            if (cnTerm.length() > 1) {
                pyTerm = cnTerm;
            }
            char c = cnTerm.charAt(0);
            if(c>='a' && c<='z'){
                pyTerm ="";
            }
            String pinyinTerm = PinyinUtil.termToPinyin(cnTerm);
            termAtt.copyBuffer(pinyinTerm.toCharArray(), 0, pinyinTerm.length());
            posIncrAtt.setPositionIncrement(1);
            cnTerm="";
            return true;
        }
        if (StringUtils.isNotEmpty(pyTerm) && term.length() >= minTermLength) {
            while (true) {
                if (StringUtils.isEmpty(pyTerm)) {
                    return false;
                }
                char c = pyTerm.charAt(0);
                pyTerm = pyTerm.substring(1);
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
            term = termAtt.toString();
            cnTerm = term;
            char c = term.charAt(0);
            if(c>='a' && c<='z'){
                cnTerm ="";
            }
            if (term.length() >= minTermLength) {
                termAtt.copyBuffer(term.toCharArray(), 0, term.length());
                posIncrAtt.setPositionIncrement(1);
                return true;
            }
            return true;
        } else {
            return false;
        }
    }
}
