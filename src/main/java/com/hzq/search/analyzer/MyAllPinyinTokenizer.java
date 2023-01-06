package com.hzq.search.analyzer;

import com.hzq.search.util.PinyinUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * 全拼分词器
 * @author Huangzq
 * @date 2022/11/17 21:40
 */
@Slf4j
public class MyAllPinyinTokenizer extends Tokenizer {
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
        String pinyin = PinyinUtil.termToPinyin(term);
        if (StringUtils.isEmpty(pinyin)) {
            termAtt.copyBuffer(new char[]{}, 0, 0);
            termAtt.resizeBuffer(0);
            termAtt.setLength(0);
            posIncrAtt.setPositionIncrement(1);
            offsetAtt.setOffset(0, 0);
            return true;
        }
        termAtt.copyBuffer(pinyin.toCharArray(), 0, pinyin.length());
        termAtt.resizeBuffer(pinyin.length());
        termAtt.setLength(pinyin.length());
        posIncrAtt.setPositionIncrement(1);
        offsetAtt.setOffset(0, pinyin.length());
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
