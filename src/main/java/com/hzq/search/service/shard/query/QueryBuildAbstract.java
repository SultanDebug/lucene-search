package com.hzq.search.service.shard.query;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hzq.search.config.FieldDef;
import com.hzq.search.enums.FieldTypeEnum;
import com.hzq.search.enums.QueryTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Huangzq
 * @description
 * @date 2023/2/22 09:38
 */
@Slf4j
public abstract class QueryBuildAbstract {
    public static Query filterHandler(String filter, Map<String, FieldDef> fieldMap) {
        BooleanQuery.Builder filterCondition = new BooleanQuery.Builder();

        JSONObject filterObj = JSON.parseObject(filter);
        AtomicBoolean keyFlag = new AtomicBoolean(false);

        filterObj.forEach((key, val) -> {
            if (fieldMap.containsKey(key)) {
                FieldDef fieldDef = fieldMap.get(key);
                if (val instanceof JSONArray) {
                    BooleanQuery.Builder builder = new BooleanQuery.Builder();
                    AtomicBoolean valFlag = new AtomicBoolean(false);
                    ((JSONArray) val).stream().forEach(o -> {
                        if (o instanceof JSONObject) {
                            JSONObject oo = (JSONObject) o;
                            Boolean left = oo.getBoolean("left");
                            Boolean right = oo.getBoolean("right");
                            if (FieldTypeEnum.INT_TYPE.getType().equals(fieldDef.getFieldType())) {
                                Integer min = oo.getInteger("min");
                                Integer max = oo.getInteger("max");
                                if (min == null) {
                                    min = Integer.MIN_VALUE;
                                } else {
                                    min = Boolean.TRUE.equals(left) ? min : Math.addExact(min, 1);
                                }

                                if (max == null) {
                                    max = Integer.MAX_VALUE;
                                } else {
                                    max = Boolean.TRUE.equals(right) ? max : Math.addExact(max, -1);
                                }

                                builder.add(IntPoint.newRangeQuery(key, min, max), BooleanClause.Occur.SHOULD);
                                valFlag.set(true);
                            } else if (FieldTypeEnum.DOUBLE_TYPE.getType().equals(fieldDef.getFieldType())) {
                                Double min = oo.getDouble("min");
                                Double max = oo.getDouble("max");
                                if (min == null) {
                                    min = Double.MIN_VALUE;
                                } else {
                                    min = Boolean.TRUE.equals(left) ? min : DoublePoint.nextUp(min);
                                }

                                if (max == null) {
                                    max = Double.MAX_VALUE;
                                } else {
                                    max = Boolean.TRUE.equals(right) ? max : DoublePoint.nextDown(max);
                                }

                                builder.add(DoublePoint.newRangeQuery(key, min, max), BooleanClause.Occur.SHOULD);
                                valFlag.set(true);
                            } else {
                                log.warn("数据{}类型{}暂未定义", o.getClass().getSimpleName(), fieldDef.getFieldType());
                            }

                        } else {
                            builder.add(new TermQuery(new Term(key, o.toString())), BooleanClause.Occur.SHOULD);
                            valFlag.set(true);
                        }
                    });
                    if (valFlag.get()) {
                        filterCondition.add(builder.build(), BooleanClause.Occur.MUST);
                        keyFlag.set(true);
                    }
                }
            } /*else {
                log.warn("字段{}未定义，请检查", key);
            }*/
        });

        return keyFlag.get() ? filterCondition.build() : null;
    }

    public abstract Pair<String , Query> buildQuery(String query, String filter, Map<String, FieldDef> fieldMap, QueryTypeEnum type);
}
