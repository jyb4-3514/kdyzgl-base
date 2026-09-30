package com.qiujie.service.hr.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 岗位取值单一真源单测：确认白名单恰为三值（店员 / 站长 / 管理员）且校验语义正确。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PositionConstantsTest {

    @Test
    @DisplayName("白名单恰为 店员 / 站长 / 管理员（顺序固定）")
    void positionsAreExactlyThree() {
        assertEquals(List.of("店员", "站长", "管理员"), PositionConstants.POSITIONS);
    }

    @Test
    @DisplayName("三个合法值均通过校验")
    void acceptsAllThree() {
        assertTrue(PositionConstants.isValid("店员"));
        assertTrue(PositionConstants.isValid("站长"));
        assertTrue(PositionConstants.isValid("管理员"));
    }

    @Test
    @DisplayName("非法值 / 空白 / null 一律拒绝")
    void rejectsIllegalValues() {
        assertFalse(PositionConstants.isValid("分拣员"));
        assertFalse(PositionConstants.isValid(""));
        assertFalse(PositionConstants.isValid("   "));
        assertFalse(PositionConstants.isValid(null));
    }

    @Test
    @DisplayName("错误文案包含全部三个合法取值")
    void invalidMessageListsAll() {
        String message = PositionConstants.invalidMessage();
        assertTrue(message.contains("店员"));
        assertTrue(message.contains("站长"));
        assertTrue(message.contains("管理员"));
    }
}
