package com.hzq.search.enums;

/**
 * @author Huangzq
 * @description
 * @date 2023/2/13 10:17
 */
public enum QueryTypeEnum {
    /**
     * 详情查询
     * */
    DETAIL_BY_COMPANY_ID("detail","详情查询"),
    /**
     * 精确查询
     * */
    PREFIX_QUERY("prefix","精确查询"),
    /**
     * 模糊查询
     * */
    FUZZY_QUERY("fuzzy","模糊查询")
    ;
    /**
     * 查询类型
     * */
    private String type;
    /**
     * 类型描述
     * */
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
}
