package com.hzq.search.service.plugin;

import com.hzq.plugins.api.PluginApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassLoaderUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sun.misc.ClassLoaderUtil;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author Huangzq
 * @description
 * @date 2023/4/3 17:25
 */
@Service
@Slf4j
public class PluginService {
    @Value("${plugin.path}")
    private String pluginPath;

    public String load() throws Exception {
        PluginService.loadJarsFromAppFolder();
        ServiceLoader<PluginApi> serviceLoader = ServiceLoader.load(PluginApi.class);
        StringBuilder res = new StringBuilder();
        for (PluginApi pluginApi : serviceLoader) {
            res.append(pluginApi.run(""));
        }
        return res.toString();
    }

    public static String getApplicationFolder() {
        String path = PluginService.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        return new File(path).getParent();
    }

    public static void loadJarsFromAppFolder() throws Exception {
        String path = getApplicationFolder() + "/plugin";
        File f = new File(path);
        if (f.isDirectory()) {
            for (File subf : f.listFiles()) {
                if (subf.isFile()) {
                    loadJarFile(subf);
                }
            }
        } else {
            loadJarFile(f);
        }
    }

    /*public static void loadJarFile(File path) throws Exception {
        URL url = path.toURI().toURL();
        // 可以获取到AppClassLoader，可以提到前面，不用每次都获取一次
//        URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{url} , Thread.currentThread().getContextClassLoader());
        // 加载
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        method.setAccessible(true);
        method.invoke(classLoader, url);
    }*/


    /*public static void loadJarFile(File path) throws Exception {
        URL url = path.toURI().toURL();
        // 可以获取到AppClassLoader，可以提到前面，不用每次都获取一次
        URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        // 加载
        Class<?>[] declaredClasses = URLClassLoader.class.getDeclaredClasses();

        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader());

        Class<?> aClass = urlClassLoader.loadClass("com.test.hzq.MyBusPlugin");

        if (aClass != null) {
            PluginApi api = (PluginApi) aClass.newInstance();
            String run = api.run("hello word");
            log.info("结果：{}", run);
        }

    }*/


    public static void loadJarFile(File path) throws Exception {
        URL url = path.toURI().toURL();

        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader());


        Class<?> aClass = urlClassLoader.loadClass("com.test.hzq.MyBusPlugin");

        if (aClass != null) {
            PluginApi api = (PluginApi) aClass.newInstance();
            String run = api.run("hello word");
            log.info("结果：{}", run);
        }

        ClassLoaderUtil.releaseLoader(urlClassLoader);

    }

}
