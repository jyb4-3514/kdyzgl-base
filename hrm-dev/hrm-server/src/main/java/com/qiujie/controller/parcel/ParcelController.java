package com.qiujie.controller.parcel;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.parcel.ParcelQuery;
import com.qiujie.dto.parcel.ParcelRankingQuery;
import com.qiujie.dto.parcel.ParcelTrendQuery;
import com.qiujie.service.parcel.ParcelService;
import com.qiujie.vo.parcel.ParcelPageVO;
import com.qiujie.vo.parcel.ParcelRankingVO;
import com.qiujie.vo.parcel.ParcelSummaryVO;
import com.qiujie.vo.parcel.ParcelTrendPointVO;
import com.qiujie.vo.parcel.ParcelVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 包裹接口（M10，6 接口，Mock {@code routes/parcel.js}，架构 §6.2 P10，大表域最后一批）。
 * <p>
 * <b>路由字面量优先</b>：{@code /summary}、{@code /trend}、{@code /ranking} 为字面量段，
 * Spring Boot 3 {@code PathPatternParser} 按模式特异性排序天然优先于 {@code /{id}}，无需人为 @Order
 * （回归断言见 {@code ParcelRouteOrderTest}）。
 * <p>
 * <b>角色门槛</b>：6 接口均 {@code ALL_ROLES}（对齐 Mock）。
 * <b>越权口径逐端点</b>：{@code GET /{id}} 跨站 → 404；{@code PUT /{id}/pickup} 跨站 → 业务码 7001；
 * 列表非 ADMIN 的 {@code stationId} 静默收敛。
 * <p>
 * <b>S7 落地</b>：分页默认游标（S7-3）、趋势预测（S7-1）、容量热力（S7-2）为可选附加能力，
 * 经 {@code hrm.algo.parcel.*} 开关控制，默认出参与 Mock 逐位一致。
 */
@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;

    /** 包裹分页列表（默认游标分页；OFFSET 兜底并限最大页深） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping
    public Result<ParcelPageVO> list(@Valid ParcelQuery query) {
        return Result.ok(parcelService.page(query));
    }

    /** 包裹看板指标（ADMIN 全站、非 ADMIN 本人驿站） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/summary")
    public Result<ParcelSummaryVO> summary() {
        return Result.ok(parcelService.summary());
    }

    /** 近 N 天入库/取件趋势（days 收敛到配置区间，默认 [1,30]） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/trend")
    public Result<List<ParcelTrendPointVO>> trend(ParcelTrendQuery query) {
        return Result.ok(parcelService.trend(query));
    }

    /** 驿站排行（可按 pickupRate / abnormalRate 重排） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/ranking")
    public Result<List<ParcelRankingVO>> ranking(ParcelRankingQuery query) {
        return Result.ok(parcelService.ranking(query));
    }

    /** 包裹详情（跨站 → 404） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/{id}")
    public Result<ParcelVO> detail(@PathVariable Long id) {
        return Result.ok(parcelService.detail(id));
    }

    /** 取件核销（不存在/跨站 7001、非可取 7002、已被他人取 7003；并发安全） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PutMapping("/{id}/pickup")
    public Result<ParcelVO> pickup(@PathVariable Long id) {
        return Result.ok(parcelService.pickup(id));
    }
}
