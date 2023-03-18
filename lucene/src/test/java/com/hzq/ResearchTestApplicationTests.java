package com.hzq;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.py.Pinyin;
import com.hankcs.hanlp.seg.common.Term;
import com.mayabot.nlp.module.pinyin.Pinyins;
import com.mayabot.nlp.module.pinyin.split.PinyinSplits;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

//@SpringBootTest
//@RunWith(SpringRunner.class)
public class ResearchTestApplicationTests {

    @Test
    public void contextLoads() {
        List<Term> s1 = HanLP.segment("啊沙发沙发地方");
        System.out.println(s1);

        List<String> s2 = PinyinSplits.split("woaibeijintiananmen");
        System.out.println(s2);



        List<String> s3 = PinyinSplits.split("fujianjinan");
        System.out.println(s3);

        List<String> s4 = PinyinSplits.split("shandongjinan");
        System.out.println(s4);

        List<String> s5 = PinyinSplits.split("jinanren");
        System.out.println(s5);

    }
}
