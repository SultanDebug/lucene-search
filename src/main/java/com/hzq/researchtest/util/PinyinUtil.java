package com.hzq.researchtest.util;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 拼音工具类
 * @author Huangzq
 * @date 2022/11/22 09:44
 */
@Slf4j
public class PinyinUtil {
    /**
     * Description:
     *  词项拼音生成
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:37
     */
    public static String termToPinyin(String source) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        //输出设置，大小写，音标方式等
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);

        StringBuilder pinyin = new StringBuilder();
        for (char c : source.toCharArray()) {
            try {
                String[] strings ;
                if(c >= 'a' && c <= 'z'){
                    strings = new String[]{String.valueOf(c)};
                }else{
                    strings = PinyinHelper.toHanyuPinyinStringArray(c,format);
                }
                if(strings!=null && strings.length>0){
                    pinyin.append(strings[0]);
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                log.error("拼音转换失败：{} ,{}",c,e.getMessage());
            }
        }

        return pinyin.toString();

    }

    /**
     * Description:
     *  词项简拼生成
     * @param
     * @return
     * @author Huangzq
     * @date 2022/12/6 19:37
     */
    public static String termToJianpin(String source) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        //输出设置，大小写，音标方式等
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);

        StringBuilder pinyin = new StringBuilder();
        for (char c : source.toCharArray()) {
            try {
                String[] strings ;
                if(c >= 'a' && c <= 'z'){
                    strings = new String[]{String.valueOf(c)};
                }else{
                    strings = PinyinHelper.toHanyuPinyinStringArray(c,format);
                }
                if(strings!=null && strings.length>0){
                    pinyin.append(strings[0].charAt(0));
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                log.error("拼音转换失败：{} ,{}",c,e.getMessage());
            }
        }

        return pinyin.toString();

    }

    public static void main(String[] args) {
        log.info(termToPinyin("荆溪白石出，Hello 天寒红叶稀。Android 山路元无雨，What's up? 空翠湿人衣。"));
        log.info(termToJianpin("荆溪白石出，Hello 天寒红叶稀。Android 山路元无雨，What's up? 空翠湿人衣。"));
    }
}
