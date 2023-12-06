package com.hzq;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() {
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.NORMAL));
        System.out.println(PinyinHelper.toPinyin("行", PinyinStyleEnum.NORMAL));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.FIRST_LETTER));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.NUM_LAST));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.INPUT));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.DEFAULT));


        System.out.println((int) ' ');
        System.out.println(Math.addExact(3, 1));
        System.out.println(Math.addExact(3, -1));


        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.NORMAL));
        System.out.println(PinyinHelper.toPinyin("行", PinyinStyleEnum.NORMAL));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.FIRST_LETTER));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.NUM_LAST));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.INPUT));
        System.out.println(PinyinHelper.toPinyin("招商银行", PinyinStyleEnum.DEFAULT));

        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();

        try {
            for (String s : net.sourceforge.pinyin4j.PinyinHelper.toHanyuPinyinStringArray('行', format)) {
                System.out.println(s);
            }

            System.out.println(net.sourceforge.pinyin4j.PinyinHelper.toHanYuPinyinString("招商银行", format, " ", false));

        } catch (BadHanyuPinyinOutputFormatCombination e) {
            throw new RuntimeException(e);
        }

    }
}
