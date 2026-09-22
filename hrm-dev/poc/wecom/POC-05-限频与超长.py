# -*- coding: utf-8 -*-
"""
POC-05  群机器人「消息推送」限频与超长行为实测（决定限频参数）

依据：hrm-dev/docs/wecom-integration-adr.md
      - 第 7 章 POC-05（最小做法 / 通过判据 / 失败判据）
      - 第 12.2 章 97xx 错误码段（9711 为系统内部限频码）
      - 第 14.1 章 W-C5（20 条/分钟是「每个机器人」的硬上限）
      - 第 19 章 V33（限频边界与超长行为）/ V35（限频计数维度）

官方依据（已核实）：
      - 企业微信帮助中心 docid=14931：「Each 'Message Push' cannot send more than 20 messages per minute」
      - 企业微信开发文档 path/91770：text 内容 ≤2048 字节；markdown 内容 ≤4096 字节
      - 40058 文案（markdown 超长）来自服务商公开文章转述，非官方原文 → 本脚本只如实记录实测返回，不预设结论

零第三方依赖：仅用 Python 标准库 urllib。

=============== 安全护栏（硬性，不可绕过）===============
  1. Webhook 只从环境变量 WECOM_POC_WEBHOOK 读取；脚本与输出中绝不出现、绝不回显完整 webhook
     （输出中只允许出现脱敏形式 ...?key=****）。变量缺失时以非 0 退出码结束，绝不回退到内置默认值。
  2. 发送总条数硬上限 MAX_TOTAL_SENDS = 30。
  3. 每条之间间隔不低于 MIN_INTERVAL_SEC = 3 秒。
  4. 支持立即停止：在输出目录放一个 POC-05-STOP.flag 文件，或按 Ctrl+C，脚本会优雅退出并写出已采集的部分结果。
  5. 必须显式传入 --confirm 才会真正发送；否则只做环境自检并退出。
  6. 仅允许在专用内部测试群执行（外部群当前证据显示不支持群机器人，见 ADR V24）。

用法：
    在目标机（驿站采集端 Windows 10）先设置环境变量（只在本窗口生效，不落盘）：
        set WECOM_POC_WEBHOOK=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<你的专用测试群 webhook key>
        rem 若有两个不同的群机器人，用于 V35 计数维度验证（可选）：
        set WECOM_POC_WEBHOOK_2=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<第二个机器人的 key>

    先自检（不发消息）：
        python "POC-05-限频与超长.py"

    确认无误后真正执行：
        python "POC-05-限频与超长.py" --confirm
"""

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime

# ==================== 硬性安全护栏常量 ====================
MAX_TOTAL_SENDS = 30          # 发送总条数硬上限
MIN_INTERVAL_SEC = 3.0        # 每条最小间隔（秒）
DEFAULT_PROBE_COUNT = 21      # 限频探测条数：21 条 × 3 秒 = 在任一 60 秒窗口内可超过 20 条
MARKDOWN_BYTES_TARGET = 4200  # 超长 markdown 构造目标字节数（> 4096）
STOP_FLAG_NAME = "POC-05-STOP.flag"
ENV_WEBHOOK = "WECOM_POC_WEBHOOK"
ENV_WEBHOOK_2 = "WECOM_POC_WEBHOOK_2"

WEBHOOK_SEND_PATH = "/cgi-bin/webhook/send"


def mask_webhook(url):
    """脱敏 webhook：只保留 key=****，绝不输出完整地址。"""
    if not url:
        return ""
    masked = re.sub(r"(?i)(key=)[^&\s]+", r"\1****", url)
    masked = re.sub(r"(?i)(webhook/send)", r"\1", masked)
    return masked


def classify_error(errcode, errmsg):
    """给实测错误码打一个中文标签，仅用于输出可读性，不做结论预设。"""
    if errcode == 0:
        return "成功"
    if errcode == 40058:
        return "markdown 内容超长（实测返回 40058）"
    if errcode in (45009, 45033, 45047):
        return "疑似频率限制类错误码（未经官方文档核实，仅如实记录）"
    return "其他错误（如实记录，不预设含义）"


def post_json(url, payload, timeout=10):
    """标准库 POST JSON；返回 (httpStatus, bodyText, errorText)。"""
    import urllib.request
    import urllib.error

    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.getcode(), body, None
    except urllib.error.HTTPError as exc:
        try:
            body = exc.read().decode("utf-8", errors="replace")
        except Exception:
            body = ""
        return exc.code, body, "HTTPError"
    except Exception as exc:
        return None, "", str(exc)


def parse_response(body):
    """解析企微返回体，取 errcode / errmsg。"""
    if not body:
        return None, ""
    try:
        obj = json.loads(body)
    except Exception:
        return None, ""
    errcode = obj.get("errcode")
    errmsg = str(obj.get("errmsg", ""))[:120]
    return errcode, errmsg


def send_once(url, payload, seq, kind, records, out_buf):
    """发送一条并留痕（时间戳 / HTTP 状态 / errcode / errmsg / 响应体长度）。"""
    ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    http_status, body, err = post_json(url, payload)
    errcode, errmsg = parse_response(body)
    rec = {
        "seq": seq,
        "kind": kind,
        "timestamp": ts,
        "httpStatus": http_status,
        "errcode": errcode,
        "errmsg": errmsg,
        "errmsgLabel": classify_error(errcode, errmsg) if errcode is not None else "无有效响应",
        "responseBodyLen": len(body or ""),
        "error": err,
    }
    records.append(rec)
    out_buf.append(rec)
    print("  [{0}] #{1} {2} http={3} errcode={4} {5}".format(
        ts, seq, kind, http_status, errcode, rec["errmsgLabel"]))
    return rec


def main():
    parser = argparse.ArgumentParser(description="POC-05 群机器人限频与超长实测（标准库实现）")
    parser.add_argument("--output-dir", default=os.path.dirname(os.path.abspath(__file__)))
    parser.add_argument("--confirm", action="store_true",
                        help="必须显式传入才会真正发送消息；否则只做环境自检")
    parser.add_argument("--interval", type=float, default=MIN_INTERVAL_SEC,
                        help="发送间隔（秒），不得低于 {0}".format(MIN_INTERVAL_SEC))
    parser.add_argument("--probe-count", type=int, default=DEFAULT_PROBE_COUNT,
                        help="限频探测条数，默认 {0}".format(DEFAULT_PROBE_COUNT))
    args = parser.parse_args()

    out_dir = args.output_dir
    out_file = os.path.join(out_dir, "POC-05-结果.json")
    stop_flag = os.path.join(out_dir, STOP_FLAG_NAME)

    # ---------- 护栏 3：间隔下限 ----------
    interval = max(float(args.interval), MIN_INTERVAL_SEC)
    if float(args.interval) < MIN_INTERVAL_SEC:
        print("[护栏] 请求的间隔 {0}s 低于硬下限，已强制提升为 {1}s".format(args.interval, MIN_INTERVAL_SEC))

    # ---------- 护栏 2：总条数上限 ----------
    probe_count = int(args.probe_count)
    planned = 1 + 1 + probe_count
    if planned > MAX_TOTAL_SENDS:
        probe_count = MAX_TOTAL_SENDS - 2
        planned = 1 + 1 + probe_count
        print("[护栏] 计划条数超上限，已将限频探测条数收敛为 {0}（总计 {1} <= {2}）".format(
            probe_count, planned, MAX_TOTAL_SENDS))

    # ---------- 护栏 1：webhook 只从环境变量读取 ----------
    hook1 = os.environ.get(ENV_WEBHOOK, "").strip()
    hook2 = os.environ.get(ENV_WEBHOOK_2, "").strip()

    result = {
        "poc": "POC-05",
        "title": "群机器人限频与超长行为实测",
        "generatedAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "env": {
            "pythonVersion": sys.version,
            "platform": sys.platform,
            "intervalSec": interval,
            "maxTotalSends": MAX_TOTAL_SENDS,
            "plannedSends": planned,
            "markdownBytesTarget": MARKDOWN_BYTES_TARGET,
            "webhook1Masked": mask_webhook(hook1),
            "webhook2Masked": mask_webhook(hook2) if hook2 else None,
            "secondWebhookProvided": bool(hook2),
        },
        "executed": False,
        "baseline": None,
        "tooLongCase": None,
        "rateLimitProbe": [],
        "crossRobotProbe": [],
        "observedLimitPerMinute": None,
        "countDimensionVerdict": "INCONCLUSIVE_SINGLE_WEBHOOK",
        "verdict": "INCONCLUSIVE",
        "evidenceNote": "",
        "redactionNote": "webhook 仅以 ...key=**** 形式出现在结果中；未落任何完整 webhook、群名、成员名。",
    }

    if not hook1:
        result["verdict"] = "FAIL"
        result["evidenceNote"] = (
            "环境变量 {0} 缺失。请先在当前窗口设置该变量（值不得写入脚本或文件），再重跑。"
            "脚本不会回退到任何内置默认值。".format(ENV_WEBHOOK)
        )
        _write(out_file, result)
        print("[失败] 未找到环境变量 {0}，已写出诊断结果：{1}".format(ENV_WEBHOOK, out_file))
        return 2

    if not hook1.startswith("https://") or WEBHOOK_SEND_PATH not in hook1:
        result["verdict"] = "FAIL"
        result["evidenceNote"] = (
            "环境变量 {0} 的取值不像企业微信 webhook 地址（应形如 https://qyapi.weixin.qq.com{1}?key=...）。"
            "请核对后重试；脚本不会猜测或拼接地址。".format(ENV_WEBHOOK, WEBHOOK_SEND_PATH)
        )
        _write(out_file, result)
        print("[失败] webhook 格式不符合预期，已写出诊断结果：{0}".format(out_file))
        return 2

    print("==== POC-05 群机器人限频与超长实测 ====")
    print("[环境] Python {0} | 间隔 {1}s | 计划发送 {2} 条（上限 {3}）".format(
        sys.version.split()[0], interval, planned, MAX_TOTAL_SENDS))
    print("[目标] webhook1 = {0}".format(mask_webhook(hook1)))
    if hook2:
        print("[目标] webhook2 = {0}（用于 V35 计数维度交叉验证）".format(mask_webhook(hook2)))
    print("[停止] 如需立即中止：在输出目录创建 {0} 文件，或在窗口按 Ctrl+C".format(STOP_FLAG_NAME))

    if not args.confirm:
        result["verdict"] = "INCONCLUSIVE"
        result["evidenceNote"] = "环境自检通过，但未传入 --confirm，未发送任何消息。确认无误后加 --confirm 执行。"
        _write(out_file, result)
        print("[自检] 未发送消息（缺少 --confirm）。已写出自检结果：{0}".format(out_file))
        return 3

    records = []
    out_buf = []
    baseline_rec = None
    too_long_rec = None
    try:
        # ---------- ① 基线 text ----------
        print("\n[1/4] 基线 text ...")
        if _stop_requested(stop_flag):
            raise KeyboardInterrupt
        baseline_rec = send_once(hook1, {
            "msgtype": "text",
            "text": {"content": "POC-05 基线测试（内部测试群，请忽略）"},
        }, 1, "baseline-text", records, out_buf)
        result["baseline"] = {
            "httpStatus": baseline_rec["httpStatus"],
            "errcode": baseline_rec["errcode"],
            "errmsg": baseline_rec["errmsg"],
        }
        time.sleep(interval)

        # ---------- ② 超长 markdown（> 4096 字节）----------
        print("\n[2/4] 超长 markdown（构造 {0} 字节）...".format(MARKDOWN_BYTES_TARGET))
        content = "# POC-05 超长测试\n" + ("A" * MARKDOWN_BYTES_TARGET)
        constructed = len(content.encode("utf-8"))
        if _stop_requested(stop_flag):
            raise KeyboardInterrupt
        too_long_rec = send_once(hook1, {
            "msgtype": "markdown",
            "markdown": {"content": content},
        }, 2, "too-long-markdown", records, out_buf)
        result["tooLongCase"] = {
            "constructedBytes": constructed,
            "httpStatus": too_long_rec["httpStatus"],
            "errcode": too_long_rec["errcode"],
            "errmsg": too_long_rec["errmsg"],
        }
        time.sleep(interval)

        # ---------- ③ 限频边界探测 ----------
        print("\n[3/4] 限频边界探测（{0} 条 × {1}s）...".format(probe_count, interval))
        for i in range(probe_count):
            if _stop_requested(stop_flag):
                print("  [停止] 检测到停止标志文件，提前结束限频探测。")
                break
            rec = send_once(hook1, {
                "msgtype": "text",
                "text": {"content": "POC-05 限频探测 #{0}".format(i + 1)},
            }, i + 3, "rate-probe", records, out_buf)
            result["rateLimitProbe"].append({
                "seq": rec["seq"],
                "timestamp": rec["timestamp"],
                "httpStatus": rec["httpStatus"],
                "errcode": rec["errcode"],
                "errmsg": rec["errmsg"],
            })
            if i < probe_count - 1:
                time.sleep(interval)

        # ---------- 观测结果推断 ----------
        rate_records = [r for r in records if r["kind"] == "rate-probe"]
        first_fail_idx = None
        for idx, r in enumerate(rate_records):
            if r["errcode"] is not None and r["errcode"] != 0:
                first_fail_idx = idx
                break
        if first_fail_idx is None:
            ok_count = sum(1 for r in rate_records if r["errcode"] == 0)
            result["observedLimitPerMinute"] = "未触发（{0} 条探测全部成功，间隔 {1}s 时约等于 20 条/分钟，未越界）".format(
                ok_count, interval)
        else:
            first_fail = rate_records[first_fail_idx]
            # 统计该失败点之前 60 秒内的成功条数（仅在有可解析时间戳时给出，属推断口径）
            result["observedLimitPerMinute"] = "首次非 0 errcode 出现在第 {0} 条探测（errcode={1}）；此前成功 {2} 条。属实测推断口径，非官方承诺值。".format(
                first_fail["seq"], first_fail["errcode"], first_fail_idx)

        # ---------- ④ V35 计数维度交叉验证 ----------
        if hook2:
            print("\n[4/4] V35 交叉验证：webhook1 疑似受限后，向 webhook2（另一个机器人）发送 2 条 ...")
            cross_ok = 0
            for j in range(2):
                if _stop_requested(stop_flag):
                    break
                rec2 = send_once(hook2, {
                    "msgtype": "text",
                    "text": {"content": "POC-05 V35 交叉验证 #{0}".format(j + 1)},
                }, 100 + j, "cross-robot", records, out_buf)
                result["crossRobotProbe"].append({
                    "seq": rec2["seq"],
                    "timestamp": rec2["timestamp"],
                    "httpStatus": rec2["httpStatus"],
                    "errcode": rec2["errcode"],
                    "errmsg": rec2["errmsg"],
                })
                if rec2["errcode"] == 0:
                    cross_ok += 1
                if j == 0:
                    time.sleep(interval)

            if cross_ok == 2:
                result["countDimensionVerdict"] = "PER_ROBOT"
                result["evidenceNote"] = "webhook2（另一机器人）在 webhook1 疑似受限后仍可正常发送 → 限频按「机器人」独立计数（V35 支持 PER_ROBOT）。"
            elif cross_ok == 0:
                result["countDimensionVerdict"] = "PER_GROUP"
                result["evidenceNote"] = "webhook2 同样被拒 → 限频不像「按机器人」独立；疑似按「群」或存在其他共享维度（如实记录，建议补充官方文档核对，V35 未完全闭环）。"
            else:
                result["countDimensionVerdict"] = "INCONCLUSIVE"
                result["evidenceNote"] = "webhook2 交叉结果不一致（1 成功 1 失败），无法判定计数维度；V35 保持未闭环。"
        else:
            result["countDimensionVerdict"] = "INCONCLUSIVE_SINGLE_WEBHOOK"
            result["evidenceNote"] = "仅提供单个 webhook，无法验证计数维度；按 W-C5 保守口径（同一机器人跨群共享配额）实现，V35 未闭环。"

        result["executed"] = True

    except KeyboardInterrupt:
        result["executed"] = True
        result["evidenceNote"] = (result["evidenceNote"] + " " if result["evidenceNote"] else "") + \
            "已按用户中止（Ctrl+C 或停止标志文件），以下为已采集的部分结果。"

    # ---------- 汇总判定 ----------
    if result["baseline"] and result["baseline"]["errcode"] == 0 and result["tooLongCase"] and result["tooLongCase"]["errcode"] is not None:
        got_40058 = (result["tooLongCase"]["errcode"] == 40058)
        tripped = any(r["errcode"] not in (0, None) for r in result["rateLimitProbe"])
        if got_40058 and tripped:
            result["verdict"] = "PASS"
            result["evidenceNote"] = (result["evidenceNote"] + " " if result["evidenceNote"] else "") + \
                "基线成功、超长返回 40058、限频边界已被触发：可用于标定令牌桶参数与内容校验。"
        elif got_40058 and not tripped:
            result["verdict"] = "INCONCLUSIVE"
            result["evidenceNote"] = (result["evidenceNote"] + " " if result["evidenceNote"] else "") + \
                "基线成功且超长返回 40058，但在 3 秒间隔护栏下未触发限频；未越界即视为「合规未触发」，如需精确边界须人工授权后另行评估。"
        elif tripped:
            result["verdict"] = "INCONCLUSIVE"
            result["evidenceNote"] = (result["evidenceNote"] + " " if result["evidenceNote"] else "") + \
                "触发限频但超长未返回预期 40058，需核对实际错误文案后回填 V33。"
        else:
            result["verdict"] = "INCONCLUSIVE"
            result["evidenceNote"] = (result["evidenceNote"] + " " if result["evidenceNote"] else "") + \
                "未观察到限频与超长预期行为，需核对 webhook 与测试群后重跑。"
    elif result["executed"]:
        result["verdict"] = "FAIL"
        base = result["baseline"] or {}
        result["evidenceNote"] = (result["evidenceNote"] + " " if result["evidenceNote"] else "") + \
            "基线发送未成功（errcode={0}），webhook 或测试群配置可能有问题，优先排查此点。".format(base.get("errcode"))

    _write(out_file, result)
    print("\n==== 结论 ====")
    print("verdict = {0}".format(result["verdict"]))
    print("countDimensionVerdict = {0}".format(result["countDimensionVerdict"]))
    print("observedLimitPerMinute = {0}".format(result["observedLimitPerMinute"]))
    print("说明：{0}".format(result["evidenceNote"]))
    print("结果已写入：{0}".format(out_file))
    return 0 if result["verdict"] in ("PASS", "INCONCLUSIVE") else 1


def _stop_requested(stop_flag):
    return os.path.exists(stop_flag)


def _write(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n[中止] 用户中断（Ctrl+C）。若已有部分结果，请检查 POC-05-结果.json。")
        sys.exit(130)
