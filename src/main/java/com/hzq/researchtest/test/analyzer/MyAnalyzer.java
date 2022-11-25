package com.hzq.researchtest.test.analyzer;

import com.bird.segment.extend.BirdExtendAnalyzer;
import com.bird.segment.extend.ExtendType;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/17 21:40
 */
public class MyAnalyzer extends Analyzer {
    BirdExtendAnalyzer birdExtendAnalyzer;

    public MyAnalyzer(BirdExtendAnalyzer birdExtendAnalyzer) {
        this.birdExtendAnalyzer = birdExtendAnalyzer;
    }

    @Override
    protected TokenStreamComponents createComponents(String s) {
        return new TokenStreamComponents(new MyTermTokenizer(birdExtendAnalyzer));
    }

    public static void main(String[] args) throws Exception {

        BirdExtendAnalyzer birdExtendAnalyzer = new BirdExtendAnalyzer();
        String modelDirNew = "D:\\MavenRepo\\com\\bird\\segment\\bird-segment-server\\2.0.6-RELEASE\\segment";
        Set<ExtendType> probExt = new HashSet();
        Set<ExtendType> compExt = new HashSet();
        probExt.add(ExtendType.CASCADE);
        compExt.add(ExtendType.CASCADE);
        compExt.add(ExtendType.SYNONYM);
        compExt.add(ExtendType.HYPERNYM);
        compExt.add(ExtendType.ARIBIC_PARSE);
        birdExtendAnalyzer.init(modelDirNew, probExt, compExt);
        //IKAnalyzer ikAnalyzer = new IKAnalyzer();


        String arr[] = {"我是中国人"};
        MyAnalyzer analyzer = new MyAnalyzer(birdExtendAnalyzer);

        for (int i = 0; i < arr.length; i++) {
            TokenStream tokenStream = analyzer.tokenStream("hzq", new StringReader(arr[i]));
            CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();//必须
            while (tokenStream.incrementToken()) {
                System.out.println(termAtt.toString());
            }
            System.out.println("===============================");
            tokenStream.close();//必须
        }
        analyzer.close();
    }
}
