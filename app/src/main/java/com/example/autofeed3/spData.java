package com.example.autofeed3;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences数据管理类
 * 用于保存和读取应用配置数据
 */
public class spData {
    private static final String PREF_NAME = "FeedData";
    
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    
    public spData() {
        // 构造函数暂时为空，需要在使用前调用 init
    }
    
    /**
     * 初始化SharedPreferences
     * @param context 上下文对象
     */
    public void init(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }
    
    /**
     * 保存字符串数据
     * @param key 键
     * @param value 值
     */
    public void saveString(String key, String value) {
        if (editor != null) {
            editor.putString(key, value);
            editor.apply();
        }
    }
    
    /**
     * 读取字符串数据
     * @param key 键
     * @param defaultValue 默认值
     * @return 读取的值，如果不存在则返回默认值
     */
    public String getString(String key, String defaultValue) {
        if (sharedPreferences != null) {
            return sharedPreferences.getString(key, defaultValue);
        }
        return defaultValue;
    }
}
