package com.qiujie.vo.sync;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 选项集出参（集合层元数据 + 选项列表）。
 */
@Data
public class SyncConfigOptionSetVO {

    private String setKey;
    private String name;
    private String description;
    private Boolean builtin;
    private Boolean enabled;
    private List<SyncConfigOptionVO> options = new ArrayList<>();
}
