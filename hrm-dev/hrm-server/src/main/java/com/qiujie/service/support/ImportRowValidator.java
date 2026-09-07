package com.qiujie.service.support;

import com.qiujie.dto.EmployeeImportRow;
import com.qiujie.util.FieldValidator;
import com.qiujie.vo.ImportErrorVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导入行级校验器（决策 D4）：
 * 纯逻辑、不依赖 Spring/DB（库内活跃账号/手机号/部门/驿站经构造参数注入），便于离线单测。
 * 校验规则与 api.md 第 5 章逐项一致；文件内账号/手机号唯一性由内部状态跟踪。
 */
public class ImportRowValidator {

    private static final int MAX_REAL_NAME_LENGTH = 50;
    private static final int MAX_REMARK_LENGTH = 255;

    /** 库内活跃登录账号 */
    private final Set<String> activeUsernames;
    /** 库内活跃手机号 */
    private final Set<String> activePhones;
    /** 部门名称 → 部门 id 列表（未删除） */
    private final Map<String, List<Long>> deptIdsByName;
    /** 驿站名称 → 驿站 id 列表（未删除，含停用） */
    private final Map<String, List<Long>> allStationIdsByName;
    /** 驿站名称 → 驿站 id 列表（未删除且启用） */
    private final Map<String, List<Long>> enabledStationIdsByName;

    /** 文件内已出现的账号/手机号（防文件内重复） */
    private final Set<String> seenUsernames = new HashSet<>();
    private final Set<String> seenPhones = new HashSet<>();

    public ImportRowValidator(Set<String> activeUsernames,
                              Set<String> activePhones,
                              Map<String, List<Long>> deptIdsByName,
                              Map<String, List<Long>> allStationIdsByName,
                              Map<String, List<Long>> enabledStationIdsByName) {
        this.activeUsernames = activeUsernames;
        this.activePhones = activePhones;
        this.deptIdsByName = deptIdsByName;
        this.allStationIdsByName = allStationIdsByName;
        this.enabledStationIdsByName = enabledStationIdsByName;
    }

    /**
     * 校验单行：返回该行全部错误（空列表=通过）；
     * 通过时回填解析字段（gender/deptId/stationId/entryDate），供入库构建实体复用。
     */
    public List<ImportErrorVO> validate(EmployeeImportRow row) {
        List<ImportErrorVO> errors = new ArrayList<>();
        int rowNumber = row.getRowNumber();

        // 1. 姓名：必填，1-50 字符
        String realName = trimToNull(row.getRealName());
        if (realName == null) {
            errors.add(new ImportErrorVO(rowNumber, "姓名", "必填，不能为空"));
        } else if (realName.length() > MAX_REAL_NAME_LENGTH) {
            errors.add(new ImportErrorVO(rowNumber, "姓名", "长度不能超过50个字符"));
        }

        // 2. 登录账号：必填、格式、库内活跃唯一、文件内唯一
        String username = trimToNull(row.getUsername());
        if (username == null) {
            errors.add(new ImportErrorVO(rowNumber, "登录账号", "必填，不能为空"));
        } else if (!FieldValidator.isValidUsername(username)) {
            errors.add(new ImportErrorVO(rowNumber, "登录账号",
                    "格式不正确，须以字母开头，4-30位，仅可含字母、数字、下划线"));
        } else if (activeUsernames.contains(username)) {
            errors.add(new ImportErrorVO(rowNumber, "登录账号", "已被使用"));
        } else if (!seenUsernames.add(username)) {
            errors.add(new ImportErrorVO(rowNumber, "登录账号", "文件内重复"));
        }

        // 3. 手机号：必填、格式、库内活跃唯一、文件内唯一
        String phone = trimToNull(row.getPhone());
        if (phone == null) {
            errors.add(new ImportErrorVO(rowNumber, "手机号", "必填，不能为空"));
        } else if (!FieldValidator.isValidPhone(phone)) {
            errors.add(new ImportErrorVO(rowNumber, "手机号", "格式不正确，须为11位有效手机号"));
        } else if (activePhones.contains(phone)) {
            errors.add(new ImportErrorVO(rowNumber, "手机号", "已被使用"));
        } else if (!seenPhones.add(phone)) {
            errors.add(new ImportErrorVO(rowNumber, "手机号", "文件内重复"));
        }

        // 4. 性别：中文枚举，留空=未知
        String genderText = trimToNull(row.getGenderText());
        if (genderText == null) {
            row.setGender(0);
        } else if ("男".equals(genderText)) {
            row.setGender(1);
        } else if ("女".equals(genderText)) {
            row.setGender(2);
        } else {
            errors.add(new ImportErrorVO(rowNumber, "性别", "取值仅支持 男/女，留空表示未知"));
        }

        // 5. 部门名称：精确匹配；不存在 / 同名多部门报错
        String deptName = trimToNull(row.getDeptName());
        if (deptName != null) {
            List<Long> deptIds = deptIdsByName.get(deptName);
            if (deptIds == null || deptIds.isEmpty()) {
                errors.add(new ImportErrorVO(rowNumber, "部门名称", "部门不存在，请先在系统中创建"));
            } else if (deptIds.size() > 1) {
                errors.add(new ImportErrorVO(rowNumber, "部门名称", "部门名称不唯一，请先规范部门命名"));
            } else {
                row.setDeptId(deptIds.get(0));
            }
        }

        // 6. 驿站名称：精确匹配；须为启用驿站；同名多驿站报错（区分不存在与已停用）
        String stationName = trimToNull(row.getStationName());
        if (stationName != null) {
            List<Long> enabledIds = enabledStationIdsByName.get(stationName);
            if (enabledIds != null && enabledIds.size() == 1) {
                row.setStationId(enabledIds.get(0));
            } else if (enabledIds != null && enabledIds.size() > 1) {
                errors.add(new ImportErrorVO(rowNumber, "驿站名称", "驿站名称不唯一，请先规范驿站命名"));
            } else {
                List<Long> allIds = allStationIdsByName.get(stationName);
                if (allIds != null && !allIds.isEmpty()) {
                    errors.add(new ImportErrorVO(rowNumber, "驿站名称", "驿站已停用，不能归属员工"));
                } else {
                    errors.add(new ImportErrorVO(rowNumber, "驿站名称", "驿站不存在，请先在系统中创建"));
                }
            }
        }

        // 7. 入职日期：yyyy-MM-dd，留空不填
        String entryDateText = trimToNull(row.getEntryDateText());
        if (entryDateText == null) {
            row.setEntryDate(null);
        } else if (!FieldValidator.isValidDate(entryDateText)) {
            errors.add(new ImportErrorVO(rowNumber, "入职日期", "格式不正确，须为 yyyy-MM-dd"));
        } else {
            row.setEntryDate(FieldValidator.parseDate(entryDateText));
        }

        // 8. 备注：≤255 字符
        String remark = trimToNull(row.getRemark());
        if (remark != null && remark.length() > MAX_REMARK_LENGTH) {
            errors.add(new ImportErrorVO(rowNumber, "备注", "长度不能超过255个字符"));
        }

        return errors;
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
