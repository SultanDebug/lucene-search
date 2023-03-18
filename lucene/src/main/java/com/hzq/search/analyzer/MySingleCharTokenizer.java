package com.hzq.search.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * 单字分词器，带归一化
 *
 * @author Huangzq
 * @date 2022/12/23 09:14
 */
@Slf4j
public class MySingleCharTokenizer extends Tokenizer {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    int offset = 0;

    @Override
    public boolean incrementToken() throws IOException {
        int c = input.read();
        if (c == -1) {
            return false;
        }

        termAtt.setEmpty();
        termAtt.append((char) c);
        posIncrAtt.setPositionIncrement(1);
        offsetAtt.setOffset(offset, offset + 1);
        offset++;
        return true;
    }

    @Override
    public void reset() throws IOException {
        offset = 0;
        termAtt.setEmpty();
        super.reset();
    }
}
