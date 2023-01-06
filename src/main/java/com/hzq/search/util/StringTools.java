package com.hzq.search.util;
import java.io.UnsupportedEncodingException;

/**
 * 字符串常用操作
 **/
public class StringTools {
    public static String normalServerString(String str) {
        String normalStr = str;
        normalStr = full2HalfChange(normalStr);
        normalStr = traditionalToSimple(normalStr);
        normalStr = normalStr.toLowerCase();
        normalStr = replaceNotWordWithStr(normalStr, Separators.SPACE);
        return normalStr;
    }

    public static String normalTrainString(String str) {
        String normalStr = str;
        normalStr = full2HalfChange(normalStr);
        normalStr = traditionalToSimple(normalStr);
        normalStr = normalStr.toLowerCase();
        normalStr = replaceNotWordWithStr(normalStr, Separators.TAB);
        //这条语句保证标点符号保留下来
        normalStr = normalStr.replaceAll("\t\t", Separators.TAB);
        //任何连续两个空白字符，都替换为空格
        normalStr = normalStr.replaceAll("[\\s]{2,}", Separators.SPACE);
        normalStr = normalStr.replaceAll("  ", Separators.SPACE);
        normalStr = normalStr.trim();
        return normalStr;
    }

    public static String normalSynonymString(String str) {
        return normalServerString(str);
    }

    /**
     * 计算字符串的长度，连续的字母为一个单词，连续的数字为一个，单个汉字为一个，空格不算长度。
     *
     * @param query   字符串
     * @param limit   截断长度
     * @param forWord true：按单词长度截断 | false：按字符长度截断
     * @return 字符串长度
     */
    public static String cutoff(String query, int limit, boolean forWord) {
        if (null == query || limit <= 0) {
            return query;
        }

        query = query.trim();

        if (!forWord) {
            //截断query
            if (query.length() > limit) {
                query = query.substring(0, limit);
            }
        } else {
            query += Separators.SPACE;

            char[] chars = query.toCharArray();
            StringBuilder sb = new StringBuilder();

            int lenght = 0;
            for (int i = 0; i < chars.length - 1; i++) {
                if (lenght >= limit) {
                    break;
                }

                sb.append(chars[i]);

                if (isSpace(chars[i])) {
                    continue;
                }

                if (isChinese(chars[i])) {
                    lenght++;
                } else if (isDigital(chars[i]) && !isDigital(chars[i + 1])) {
                    lenght++;
                } else if (isAlphabet(chars[i]) && !isAlphabet(chars[i + 1])) {
                    lenght++;
                }
            }

            query = sb.toString().trim();
        }

        return query;
    }


    /**
     * 判断字符是否是中文字符
     *
     * @param c 字符
     * @return 是否是中文字符
     */
    public static boolean isChinese(char c) {
        int code = (int) c;

        //0x4E00 - 0x9FA5之间不只包含中文字符，还包括一部分标点符号
        int chineseAscllStart = 0x4E00, chineseAscllEnd = 0x9FA5;
        boolean beChineseAscll = code >= chineseAscllStart && code <= chineseAscllEnd;
        if (beChineseAscll && Character.isLetter(c)) {
            return true;
        }

        return false;
    }

    /**
     * 判断字符是否是数字
     *
     * @param c 字符
     * @return 是否是数字
     */
    public static boolean isDigital(char c) {
        return Character.isDigit(c);
    }

    /**
     * 判断字符是否是英文字母
     *
     * @param c 字符
     * @return 是否是英文字母
     */
    public static boolean isAlphabet(char c) {
        int code = (int) c;
        //小写英文字母，半角
        //大写英文字母，半角
        //小写英文字母，全角
        //大写英文字母，全角
        return ((code >= 97 && code <= 122) ||
                (code >= 65 && code <= 90) ||
                (code >= 65345 && code <= 65370) ||
                (code >= 65313 && code <= 65338));
    }

    /**
     * 判断字符是否是空白字符
     *
     * @param c 字母
     * @return 是否是空白字符
     */
    public static boolean isSpace(char c) {
        return Character.isWhitespace(c);
    }


    /**
     * 对输入进行全角转半角转换
     *
     * @param qJstr
     * @return
     */
    private static String full2HalfChange(String qJstr) {
        if (qJstr == null || qJstr.length() == 0) {
            return "";
        }

        StringBuffer outStrBuf = new StringBuffer("");
        String tstr = "";

        byte[] b = null;
        for (int i = 0; i < qJstr.length(); i++) {
            tstr = qJstr.substring(i, i + 1);
            if (" ".equals(tstr)) {
                outStrBuf.append(Separators.SPACE);
                continue;
            }

            try {
                b = tstr.getBytes("unicode");

                if (b[2] == -1) {
                    //表示全角
                    b[3] = (byte) (b[3] + 32);
                    b[2] = 0;
                    outStrBuf.append(new String(b, "unicode"));
                } else {
                    outStrBuf.append(tstr);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return outStrBuf.toString();
    }

    /**
     * 去除所有的非文字符号(除了",","."和空格保留)，并用replaceStr替代
     *
     * @param input
     * @param replaceStr
     * @return
     */
    private static String replaceNotWordWithStr(final String input, String replaceStr) {
        final StringBuilder builder = new StringBuilder();
        for (final char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.isLowerCase(c) ? c : Character.toLowerCase(c));
            } else {
                if (c == Separators.CHAR_HORIZONTAL_BAR || c == Separators.CHAR_DOT || Character.isSpaceChar(c)) {
                    builder.append(c);
                } else {
                    builder.append(replaceStr);
                }
            }
        }

        String newStr = builder.toString();
        return newStr;
    }

    private static String traditionalToSimple(String tradStr) {
        String simplifiedStr = ChineseWordUtils.toSimplified(tradStr);
        return simplifiedStr;
    }

    public static void main(String[] args){
        String str = "野生海捕大虾新鲜冷冻活虾厄瓜多尔尔尔白虾基围虾对虾非青岛大虾包邮";

        System.out.println(str);
    }
}
