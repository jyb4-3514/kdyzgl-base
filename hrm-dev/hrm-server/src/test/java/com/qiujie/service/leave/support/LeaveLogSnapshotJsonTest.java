package com.qiujie.service.leave.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiujie.entity.LeaveLog;
import com.qiujie.util.JsonUtil;
import com.qiujie.vo.leave.LeaveLogVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * leave_log 快照 JSON 文本解析与 {@link LeaveLogVO} 出参契约单测（配合列名转义修复的回归护栏）。
 * <p>
 * 覆盖 {@code LeaveServiceImpl.parseJsonObject}（私有，唯一委托 {@link JsonUtil#read(String, TypeReference)}）
 * 的容错边界，及出参字段名稳定性（列名转义<b>不得</b>改变 HTTP 契约）。
 * <p>边界：Service 的私有 {@code toLogVO} / {@code pushLog} 调用链未直接执行（无 Spring/DB 上下文），
 * 此处以「唯一解析委托 + VO 序列化」等价覆盖；整链与实跑收敛到服务器阶段。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class LeaveLogSnapshotJsonTest {

    private static Object parse(String json) {
        return JsonUtil.read(json, new TypeReference<Object>() {
        });
    }

    @Test
    @DisplayName("null / 空串 / 空白文本 → null（DB 列可为 NULL，按「未配置」处理）")
    void blankSnapshotReturnsNull() {
        assertNull(parse(null));
        assertNull(parse(""));
        assertNull(parse("   "));
    }

    @Test
    @DisplayName("合法 JSON → 对象 / 数组 / 标量（结构随动作而变，出参用 Object 承载）")
    void validSnapshotParsesToObject() {
        Object obj = parse("{\"reason\":\"x\",\"days\":1}");
        assertInstanceOf(Map.class, obj);
        assertEquals("x", ((Map<?, ?>) obj).get("reason"));

        assertInstanceOf(List.class, parse("[1,2,3]"));
        assertEquals("t", parse("\"t\""));
    }

    @Test
    @DisplayName("非法 JSON → 快速失败 IllegalStateException（MySQL JSON 列保证合法，DB 读取路径不可达）")
    void invalidSnapshotFailsFast() {
        // leave_log.before/after 为 MySQL JSON 类型，写入即校验；库中不可能存在非法 JSON。
        // 故此处 fail-fast 不影响生产读取，仅暴露调用方误传非 JSON 文本的场景。
        assertThrows(IllegalStateException.class, () -> parse("{bad-json"));
    }

    @Test
    @DisplayName("出参字段名保持 before/after（HTTP 契约不变，仅列名转义）")
    void voKeepsBeforeAfterFieldNames() throws Exception {
        LeaveLogVO vo = new LeaveLogVO();
        vo.setAction("UPDATE");
        vo.setBefore(Map.of("reason", "before-value"));
        vo.setAfter(Map.of("reason", "after-value"));

        String json = new ObjectMapper().writeValueAsString(vo);
        assertTrue(json.contains("\"before\""), "出参须含 before 字段");
        assertTrue(json.contains("\"after\""), "出参须含 after 字段");
        assertTrue(json.contains("\"action\""), "出参须含 action 字段");
    }

    @Test
    @DisplayName("插入→读取回环：write 输出可被等价解析；空快照保持 NULL 而非字面 \"null\"")
    void insertReadRoundTrip() {
        LeaveLog row = new LeaveLog();
        row.setBefore(JsonUtil.write(Map.of("status", "PENDING")));
        row.setAfter(JsonUtil.write(null));

        assertNull(row.getAfter(), "空 after 须保持 NULL，不写 \"null\" 字面量");
        Object parsed = parse(row.getBefore());
        assertInstanceOf(Map.class, parsed);
        assertEquals("PENDING", ((Map<?, ?>) parsed).get("status"));
    }
}
