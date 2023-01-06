package com.hzq.researchtest.log;

/**
 * @author Huangzq
 * @description
 * @date 2022/9/7 16:28
 */
public interface CommonConstants {
    /**
     * 日志id标识名称
     */
    String TRACE_ID = "X-TraceId";

    /**
     * 基础成功返回编码
     */
    int SUCCESS_CODE = 0;
    /**
     * 基础成功返回描述
     */
    String SUCCESS_MSG = "请求成功";
    /**
     * 基础异常返回编码
     */
    int ERROR_CODE = 400;
    /**
     * 基础异常返回描述
     */
    String ERROR_MSG = "系统异常";
}
