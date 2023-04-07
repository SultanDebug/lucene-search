package com.hzq.search.service.plugin;

import com.hzq.plugins.annotations.MyPlugin;
import com.hzq.plugins.api.PluginApi;

/**
 * @author Huangzq
 * @description
 * @date 2023/4/3 17:23
 */
@MyPlugin(desc = "default")
public class DefaultPlugin implements PluginApi {
    @Override
    public String run(String para) {
        return "default";
    }


}
