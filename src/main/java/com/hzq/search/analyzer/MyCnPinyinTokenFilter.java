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
    private int start = 0;
    private int skippedPositions = 0;
    private int pos = 1;
    private String term;
    /**
     * Term最小长度，小于这个长度的不进行拼音转换
     **/
    private int minTermLength;

    private String pyTerm;

    private String cnTerm;
    private boolean singleFlag = true;
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
        if (StringUtils.isNotEmpty(cnTerm)) {

            String pinyinTerm = PinyinUtil.termToPinyin(cnTerm);
            termAtt.copyBuffer(pinyinTerm.toCharArray(), 0, pinyinTerm.length());
            posIncrAtt.setPositionIncrement(pos);
            skippedPositions = 0;
            start = offsetAtt.startOffset();
            if (cnTerm.length() > 1) {
                pyTerm = cnTerm;
            } else {
                pos++;
            }
            cnTerm = "";
            return true;
        }
        if (StringUtils.isNotEmpty(pyTerm) && pyTerm.length() >= minTermLength) {
            while (true) {
                if (StringUtils.isEmpty(pyTerm)) {
                    return false;
                }
                char c = pyTerm.charAt(0);
                if (singleFlag) {
                    termAtt.setEmpty();
                    termAtt.append(c);
                    posIncrAtt.setPositionIncrement(pos);
                    int startTmp = start + skippedPositions;
                    offsetAtt.setOffset(startTmp, startTmp + 1);
                    skippedPositions++;
                    pos++;
                    singleFlag = false;
                    return true;
                }
                pyTerm = pyTerm.substring(1);
                String pinyinTerm = PinyinUtil.termToPinyin(String.valueOf(c));
                if (StringUtils.isEmpty(pinyinTerm)) {
                    continue;
                }
                termAtt.setEmpty();
                termAtt.append(pinyinTerm);
                //posIncrAtt.setPositionIncrement(pos);
                singleFlag = true;
                return true;
            }
        }
        if (input.incrementToken()) {
            term = termAtt.toString();
            cnTerm = term;
            if (term.length() >= minTermLength) {
                termAtt.copyBuffer(term.toCharArray(), 0, term.length());
                posIncrAtt.setPositionIncrement(pos);
                char c = term.charAt(0);
                //英文、拼音词项阻断
                if (c >= 'a' && c <= 'z') {
                    pos++;
                    cnTerm = "";
                }
                return true;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pos = 1;
    }
}
