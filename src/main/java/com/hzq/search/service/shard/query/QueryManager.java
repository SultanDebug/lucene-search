package com.hzq.search.service.shard.query;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Huangzq
 * @description
 * @date 2023/2/22 09:43
 */
public class QueryManager {
    private static Map<String,QueryBuildAbstract> QUERY_MAP = new HashMap<>();

    public static QueryBuildAbstract getQueryBuild(String key) {
        return QUERY_MAP.get(key);
    }

    public static void setQueryMap(String key,QueryBuildAbstract builder) {
        QUERY_MAP.put(key,builder);
    }
}
