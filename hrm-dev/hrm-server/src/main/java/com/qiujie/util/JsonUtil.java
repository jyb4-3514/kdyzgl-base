package com.qiujie.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 序列化/反序列化工具（JSON 列读写用）。
 * <p>
 * 为什么自带而非注入 Spring 的 ObjectMapper：MyBatis 类型处理器（{@code BaseTypeHandler}）由 MyBatis 直接 new，
 * 不在 Spring 容器内，无法注入；且考勤 JSON 列结构简单（无日期类型），自带一个静态实例即可，
 * 避免为「JSON 列」把 ObjectMapper 暴露成静态全局。静态 ObjectMapper 线程安全（官方保证，配置后不再改动）。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // 未知字段不报错：JSON 列结构随业务演进（如 wifi_list 增加字段），旧代码读新数据不应直接失败
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {
    }

    /** 对象 → JSON 字符串；null 返回 null（保持数据库列为 NULL 而非字面 "null"） */
    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败: " + value.getClass().getName(), e);
        }
    }

    /** JSON 字符串 → 对象；空串/null 返回 null（交由调用方按「未配置」处理） */
    public static <T> T read(String json, JavaType type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败: " + json, e);
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败: " + json, e);
        }
    }

    /** 构造 {@code List<elementType>} 的 JavaType（供类型处理器显式声明泛型，规避 List 擦除） */
    public static JavaType listType(Class<?> elementType) {
        return MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, elementType);
    }

    /** 构造 {@code Map<String, Object>} 的 JavaType（供同步配置约束/附加属性 JSON 列使用） */
    public static JavaType mapType() {
        return MAPPER.getTypeFactory().constructMapType(java.util.Map.class, String.class, Object.class);
    }
}
