package com.hzq.search.util;

import lombok.Data;

import java.io.Serializable;

/**
 * @author Huangzq
 * @description
 * @date 2022/9/27 10:17
 */
@Data
public class Position implements Serializable {
    private final int fieldId;
    private final int offset;

    public Position(int fieldId, int offset) {
        this.fieldId = fieldId;
        this.offset = offset;
    }
}
