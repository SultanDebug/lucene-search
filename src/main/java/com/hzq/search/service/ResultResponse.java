package com.hzq.search.service;

import lombok.Data;

import java.io.Serializable;

/**
 * 全局返回类型
 *
 * @author 黄震强
 * @version 1.0.0
 * @date 2020/6/1 11:53
 */
@Data
public class ResultResponse<T> implements Serializable {
    private static final long serialVersionUID = 9064214930247718458L;
    private String code;
    private String msg;
    private T data;

    public static<T> ResultResponse<T> success(){
        ResultResponse<T> res = new ResultResponse<T>();
        res.setCode("200");
        return res;
    }

    public static<T> ResultResponse<T> success(T data){
        ResultResponse<T> res = new ResultResponse<T>();
        res.setCode("200");
        res.setData(data);
        return res;
    }

    public static<T> ResultResponse<T> fail(String code , String msg){
        ResultResponse<T> res = new ResultResponse<T>();
        res.setCode(code);
        res.setMsg(msg);
        return res;
    }
}
