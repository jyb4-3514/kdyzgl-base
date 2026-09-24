package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.AuthTrustedDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 受信设备 Mapper（表 {@code auth_trusted_device}，V15）。
 * <p>
 * 仅用 BaseMapper 提供的参数化 CRUD（禁止拼接 SQL，规则 §4）；查询条件一律由
 * {@code TrustedDeviceRegistry} 以 LambdaQueryWrapper 组装，字段名由实体映射保证。
 */
@Mapper
public interface AuthTrustedDeviceMapper extends BaseMapper<AuthTrustedDevice> {
}
