package com.qiujie.service.support;

import com.qiujie.dto.employee.EmployeeImportRow;
import com.qiujie.vo.employee.ImportErrorVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入行级校验器单测（纯逻辑，库内数据以测试构造注入，不依赖 DB/Redis）。
 */
class ImportRowValidatorTest {

    private ImportRowValidator validator;

    @BeforeEach
    void setUp() {
        // 模拟库内数据：已有账号 admin、已有手机号 13800000000；
        // 部门「总公司」唯一；「运营部」存在两个同名（不唯一）；
        // 驿站「城东驿站」启用、「城南驿站」停用、「城西驿站」同名两个启用
        Map<String, List<Long>> deptIdsByName = Map.of(
                "总公司", List.of(1L),
                "运营部", List.of(2L, 3L));
        Map<String, List<Long>> allStationIdsByName = Map.of(
                "城东驿站", List.of(10L),
                "城南驿站", List.of(11L));
        Map<String, List<Long>> enabledStationIdsByName = Map.of(
                "城东驿站", List.of(10L));
        validator = new ImportRowValidator(
                Set.of("admin"),
                Set.of("13800000000"),
                deptIdsByName,
                allStationIdsByName,
                enabledStationIdsByName);
    }

    private EmployeeImportRow buildValidRow(int rowNumber) {
        EmployeeImportRow row = new EmployeeImportRow();
        row.setRowNumber(rowNumber);
        row.setRealName("张三");
        row.setUsername("zhangsan");
        row.setPhone("13912345678");
        row.setGenderText("男");
        row.setDeptName("总公司");
        row.setStationName("城东驿站");
        row.setEntryDateText("2026-09-01");
        row.setRemark("测试备注");
        return row;
    }

    @Test
    void validRowPassesAndResolvesFields() {
        EmployeeImportRow row = buildValidRow(2);
        List<ImportErrorVO> errors = validator.validate(row);

        assertTrue(errors.isEmpty());
        assertEquals(1, row.getGender());
        assertEquals(1L, row.getDeptId());
        assertEquals(10L, row.getStationId());
        assertEquals(LocalDate.of(2026, 9, 1), row.getEntryDate());
    }

    @Test
    void blankOptionalFieldsAreAllowed() {
        EmployeeImportRow row = buildValidRow(2);
        row.setGenderText("");
        row.setDeptName(null);
        row.setStationName("  ");
        row.setEntryDateText("");
        row.setRemark(null);

        List<ImportErrorVO> errors = validator.validate(row);
        assertTrue(errors.isEmpty());
        assertEquals(0, row.getGender()); // 留空=未知
        assertNull(row.getDeptId());
        assertNull(row.getStationId());
        assertNull(row.getEntryDate());
    }

    @Test
    void missingRequiredFieldsReported() {
        EmployeeImportRow row = buildValidRow(5);
        row.setRealName(null);
        row.setUsername("");
        row.setPhone(null);

        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(e ->
                e.getRow() == 5 && "姓名".equals(e.getField()) && e.getMessage().contains("必填")));
        assertTrue(errors.stream().anyMatch(e ->
                e.getRow() == 5 && "登录账号".equals(e.getField()) && e.getMessage().contains("必填")));
        assertTrue(errors.stream().anyMatch(e ->
                e.getRow() == 5 && "手机号".equals(e.getField()) && e.getMessage().contains("必填")));
    }

    @Test
    void duplicateUsernameAgainstDbRejected() {
        EmployeeImportRow row = buildValidRow(2);
        row.setUsername("admin"); // 库内已有
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("登录账号", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("已被使用"));
    }

    @Test
    void duplicatePhoneInsideFileRejected() {
        EmployeeImportRow first = buildValidRow(2);
        first.setPhone("13911112222");
        assertTrue(validator.validate(first).isEmpty());

        EmployeeImportRow second = buildValidRow(3);
        second.setUsername("lisi");
        second.setPhone("13911112222"); // 与文件内第一行重复
        List<ImportErrorVO> errors = validator.validate(second);
        assertEquals(1, errors.size());
        assertEquals("手机号", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("文件内重复"));
    }

    @Test
    void duplicateUsernameInsideFileRejected() {
        assertTrue(validator.validate(buildValidRow(2)).isEmpty());
        EmployeeImportRow second = buildValidRow(3);
        second.setPhone("13999999999");
        second.setUsername("zhangsan"); // 文件内重复
        List<ImportErrorVO> errors = validator.validate(second);
        assertEquals(1, errors.size());
        assertEquals("登录账号", errors.get(0).getField());
    }

    @Test
    void invalidPhoneFormatRejected() {
        EmployeeImportRow row = buildValidRow(7);
        row.setPhone("1391234567"); // 10 位
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("手机号", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("11位有效手机号"));
    }

    @Test
    void invalidGenderRejected() {
        EmployeeImportRow row = buildValidRow(4);
        row.setGenderText("未知"); // 仅支持 男/女/空
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("性别", errors.get(0).getField());
    }

    @Test
    void ambiguousDeptNameRejected() {
        EmployeeImportRow row = buildValidRow(6);
        row.setDeptName("运营部"); // 同名两个部门
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("部门名称", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("不唯一"));
    }

    @Test
    void unknownDeptRejected() {
        EmployeeImportRow row = buildValidRow(6);
        row.setDeptName("不存在的部门");
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("部门名称", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("不存在"));
    }

    @Test
    void disabledStationRejected() {
        EmployeeImportRow row = buildValidRow(8);
        row.setStationName("城南驿站"); // 存在但已停用
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("驿站名称", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("已停用"));
    }

    @Test
    void unknownStationRejected() {
        EmployeeImportRow row = buildValidRow(8);
        row.setStationName("不存在的驿站");
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("驿站名称", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("不存在"));
    }

    @Test
    void invalidEntryDateFormatRejected() {
        EmployeeImportRow row = buildValidRow(9);
        row.setEntryDateText("2026/09/01");
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("入职日期", errors.get(0).getField());
        assertTrue(errors.get(0).getMessage().contains("yyyy-MM-dd"));
    }

    @Test
    void multipleErrorsInOneRowAllCollected() {
        EmployeeImportRow row = buildValidRow(10);
        row.setUsername("1bad");       // 格式错误
        row.setPhone("13800000000");   // 库内重复
        row.setGenderText("x");        // 性别非法
        row.setEntryDateText("bad");   // 日期非法

        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(4, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.getRow() == 10));
    }

    @Test
    void remarkTooLongRejected() {
        EmployeeImportRow row = buildValidRow(11);
        row.setRemark("长".repeat(256));
        List<ImportErrorVO> errors = validator.validate(row);
        assertEquals(1, errors.size());
        assertEquals("备注", errors.get(0).getField());
    }
}
