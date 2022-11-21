package com.hzq.researchtest.test.analyzer;

import com.bird.segment.core.common.Token;
import com.bird.segment.extend.BirdExtendAnalyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import java.io.IOException;
import java.util.List;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 21:40
 */
public class MyTermNoResetTokenizer extends Tokenizer {
    private BirdExtendAnalyzer birdExtendAnalyzer ;

    public MyTermNoResetTokenizer(BirdExtendAnalyzer birdExtendAnalyzer){
        this.birdExtendAnalyzer = birdExtendAnalyzer;
    }


    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    private int listPos;
    String query;
    List<Token> list;


    @Override
    public boolean incrementToken() throws IOException {
        if(query==null){
            char[] buffer = new char[50];
            int index = 0;
            int  c = -1;
            while( (c=input.read()) != -1 ) {
                if(index==49)break;
                buffer[index++] = (char)c;
            }
            query =  new String(buffer,0,index);
            list = birdExtendAnalyzer.probabilitySegment(query);
        }
        if(listPos>=list.size()){return false;}
        Token token = list.get(listPos);
        int start = listPos==0?0:list.get(listPos-1).getOffset()+list.get(listPos-1).getTerm().length();
        int end = token.offset+token.getTerm().length();

        char[] buffer = termAtt.buffer();
        int pos = 0;
        for (char c : token.term.toCharArray()) {
            if (pos >= buffer.length-1)
                buffer = termAtt.resizeBuffer(token.term.length()+buffer.length);
            buffer[pos++] = c;
        }
        termAtt.resizeBuffer(token.term.length());
        termAtt.setLength(token.term.length());

        offsetAtt.setOffset(start,end);
        listPos++;
        return true;
    }

    @Override
    public void close() throws IOException {
        super.close();
        listPos = 0;
        query = null;
    }
}
