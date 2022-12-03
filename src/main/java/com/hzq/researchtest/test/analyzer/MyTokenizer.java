package com.hzq.researchtest.test.analyzer;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import java.io.IOException;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 21:40
 */
public class MyTokenizer /*extends Tokenizer*/ {
    /*private BirdExtendAnalyzer birdExtendAnalyzer;

    public MyTokenizer(BirdExtendAnalyzer birdExtendAnalyzer) {
        this.birdExtendAnalyzer = birdExtendAnalyzer;
    }

    private static char[] levelArr = {'省', '市', '县', '镇', '区'};

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    private int startoffset, endoffset;

    @Override
    public boolean incrementToken() throws IOException {
        char[] buffer = termAtt.buffer();
        int index = 0;
        int c = -1;
        while ((c = input.read()) != -1) {
            if (index == buffer.length)
                buffer = termAtt.resizeBuffer(8 + buffer.length);
            buffer[index++] = (char) c;
            if (isSplitChar((char) c)) {
                break;
            }
        }

        startoffset = endoffset;
        endoffset += index;
        if (startoffset != endoffset) {
            termAtt.setLength(index);
            offsetAtt.setOffset(startoffset, endoffset);
        }

        if (c == -1 && index <= 0) {
            return false;
        }
        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        startoffset = 0;
        endoffset = 0;
    }

    private boolean isSplitChar(char c) {
        for (char item : levelArr) {
            if (item == c) {
                return true;
            }
        }
        return false;
    }*/


}
