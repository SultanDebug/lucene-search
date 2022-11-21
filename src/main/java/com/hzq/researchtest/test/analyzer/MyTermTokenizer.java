package com.hzq.researchtest.test.analyzer;

import com.bird.segment.core.common.Token;
import com.bird.segment.extend.BirdExtendAnalyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import java.io.IOException;
import java.util.List;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 21:40
 */
public class MyTermTokenizer extends Tokenizer {
    private BirdExtendAnalyzer birdExtendAnalyzer ;

    String query = null;
    List<Token> list=null;

    int listPos = 0;
    int endFlag = 0;

    public MyTermTokenizer(BirdExtendAnalyzer birdExtendAnalyzer){
        this.birdExtendAnalyzer = birdExtendAnalyzer;
    }


    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);


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
            input.reset();
        }
        if(listPos>=list.size()){return false;}

        Token token = list.get(listPos);

        int start = listPos==0?0:list.get(listPos-1).getOffset()+list.get(listPos-1).getTerm().length();
        int end = token.offset+token.getTerm().length();

        int index = start;
        int pos = 0;
        int  c = -1;
        char[] buffer = termAtt.buffer();
        while( true ) {
            if(index==end)break;
            if((c=input.read()) == -1){break;}
            buffer[pos++] = (char)c;
            index++;
        }

        listPos++;

        termAtt.resizeBuffer(token.term.length());
        termAtt.setLength(token.term.length());
        offsetAtt.setOffset(start,end);

        if(c == -1 && index <=0 ) {
            return false;
        }
        return true;
    }


    @Override
    public void close() throws IOException {
        super.close();
        listPos = 0;
        query = null;
    }
}
