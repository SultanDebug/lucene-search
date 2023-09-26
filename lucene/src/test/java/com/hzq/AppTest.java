package com.hzq;

import static org.junit.Assert.assertTrue;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue()
    {
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.NORMAL));
        System.out.println(PinyinHelper.toPinyin("行", PinyinStyleEnum.NORMAL));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.FIRST_LETTER));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.NUM_LAST));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.INPUT));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.DEFAULT));
    }
}
