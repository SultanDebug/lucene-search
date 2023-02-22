package com.hzq.search;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.py.Pinyin;
import com.hankcs.hanlp.seg.common.Term;
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
        List<Term> woaibeijingtiananmen = HanLP.segment("啊沙发沙发地方");
        System.out.println(woaibeijingtiananmen);
    }
}
