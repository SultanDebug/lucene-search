package com.hzq.search.enums;

/**
 * @author Huangzq
 * @description
 * @date 2023/2/13 10:17
 */
public enum FieldTypeEnum {
    /**
     * 整型
     */
    INT_TYPE(3, "整型"),
    /**
     * 双精度
     */
    DOUBLE_TYPE(4, "双精度"),
    ;
    /**
     * 数据类型
     */
    private Integer type;
    /**
     * 类型描述
     */
    private String desc;

    FieldTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }
}
