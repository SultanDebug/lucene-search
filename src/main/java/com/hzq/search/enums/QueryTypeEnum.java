package com.hzq.search.enums;

/**
 * @author Huangzq
 * @description
 * @date 2023/2/13 10:17
 */
public enum QueryTypeEnum {
    /**
     * id详情查询
     */
    DETAIL_BY_COMPANY_ID("detail", "id详情查询"),
    /**
     * 精确查询
     */
    PREFIX_QUERY("prefix", "精确查询"),
    /**
     * 模糊查询
     */
    FUZZY_QUERY("fuzzy", "模糊查询"),

    /**
     * 拼音短语查询
     */
    PINYIN_QUERY("pinyin", "拼音短语查询"),
    /**
     * 单字模糊查询
     */
    SINGLE_FUZZY_QUERY("single_fuzzy", "单字模糊查询"),

    /**
     * 单字模糊查询
     */
    SINGLE_NAME_QUERY("single_name", "name字段单字模糊查询"),
    /**
     * 复合查询
     */
    COMPLEX_QUERY("complex", "复合查询");
    /**
     * 查询类型
     */
    private String type;
    /**
     * 类型描述
     */
    private String desc;

    QueryTypeEnum(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    public static QueryTypeEnum findByType(String type) {
        for (QueryTypeEnum value : QueryTypeEnum.values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return null;
    }
}
