package com.hzq.search.util;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Huangzq
 * @description
 * @date 2023/9/19 11:53
 */
public class PinyinAlphabetDict {
    private static PinyinAlphabetDict instance;
    private String[] dict = {"a", "ai", "an", "ang", "ao", "b", "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
            "c", "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "ch", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua",
            "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo", "d", "da", "dai", "dan", "dang", "dao", "de", "dei", "den",
            "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo", "e", "er", "f", "fa", "fan", "fang", "fei", "fen", "feng",
            "fiao", "fo", "fou", "fu", "g", "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            "h", "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo", "i", "j", "ja", "ji", "jia",
            "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun", "k", "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou",
            "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo", "l", "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin",
            "ling", "liu", "lo", "long", "lou", "lu", "luan", "lun", "luo", "lv", "lve", "lü", "lüe", "m", "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian",
            "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu", "n",
            "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nun", "nuo",
            "nv", "nve", "nü", "nüe", "o", "p", "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu", "q", "qi",
            "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun", "r", "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong",
            "rou", "ru", "ruan", "rui", "run", "ruo", "s", "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sh", "sha", "shai", "shan", "shang", "shao", "she", "shei",
            "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo", "t", "ta",
            "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo", "u", "v", "w", "wa", "wai", "wan",
            "wang", "wei", "wen", "weng", "wo", "wu", "x", "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun", "y", "ya", "yai",
            "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun", "z", "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zh",
            "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong",
            "zou", "zu", "zuan", "zui", "zun", "zuo", "ü",
    };
    private Set<String> alphabet = new HashSet<String>();

    private PinyinAlphabetDict() {
        for (String w : dict) {
            alphabet.add(w);
        }
    }

    public static PinyinAlphabetDict getInstance() {
        if (instance == null) {
            synchronized (PinyinAlphabetDict.class) {
                if (instance == null) {
                    instance = new PinyinAlphabetDict();
                }
            }
        }
        return instance;
    }

    public boolean match(String c) {
        return alphabet.contains(c);
    }
}
