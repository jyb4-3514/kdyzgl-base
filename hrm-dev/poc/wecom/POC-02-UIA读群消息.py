# -*- coding: utf-8 -*-
"""
POC-02  UIA 读群消息 —— 备选方案（仅当采集端机器「恰好已装 Python 3.x」时使用）

⚠️ 前置依赖：pip install pywinauto  —— 需安装第三方包，可能不被目标机策略允许。
   目标机为「零安装优先」的驿站采集端 Windows 10 机器，故【主方案】是同目录的
   POC-02-UIA读群消息.ps1（纯 PowerShell + .NET Framework，零安装）。
   只有在 PowerShell 方案不可用、且用户明确同意安装依赖时，才使用本脚本。

依据：hrm-dev/docs/wecom-integration-adr.md 第 7 章 POC-02、第 19 章 V31
红线：只读。不做注入、不读内存、不发送消息。
脱敏：与 PowerShell 版本一致——会话名 GROUP_n、发送者 MEMBER_n、文本≤60 字符并掩码敏感项。

用法：
    python "POC-02-UIA读群消息.py" --run-index 1
    python "POC-02-UIA读群消息.py" --run-index 2 --redact-mode full

不确定点（未在开发机验证，因本机无企微客户端、无 Python 运行环境）：
    1. pywinauto 的 Desktop/Application 连接与 element_info 属性名以官方文档为准；
       本脚本对属性访问全部做了 try/except 兜底，若个别属性名不同，会退化为空值而非崩溃。
    2. 企微 WebView 内容是否暴露到 UIA 树，取决于 Chromium 的无障碍开关，需实测。
"""

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime

# 遍历保护上限，防止深树导致长时间卡住
MAX_DEPTH = 25
MAX_NODES = 6000
TIME_BUDGET_SEC = 90

PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
EMAIL_RE = re.compile(r"[\w.\-]+@[\w\-]+\.[\w.\-]+")
IDCARD_RE = re.compile(r"\d{17}[\dXx]")


def protect_text(text, max_len=60):
    """文本脱敏：手机号仅留后 4 位，邮箱/身份证占位，超长截断。"""
    if not text:
        return text
    s = PHONE_RE.sub(lambda m: "****" + m.group(0)[-4:], text)
    s = EMAIL_RE.sub("<EMAIL>", s)
    s = IDCARD_RE.sub("<IDCARD>", s)
    if len(s) > max_len:
        s = s[:max_len] + "…"
    return s


def get_element_info(el):
    """兼容 UIAWrapper 与 WindowSpecification：统一返回 element_info 对象。"""
    ei = getattr(el, "element_info", None)
    if ei is not None:
        return ei
    wrapper_object = getattr(el, "wrapper_object", None)
    if callable(wrapper_object):
        try:
            inner = wrapper_object()
            return getattr(inner, "element_info", None)
        except Exception:
            return None
    return None


def node_info(el, depth):
    """读取单个 UIA 节点的语义属性，全部属性访问均兜底。"""
    ei = get_element_info(el)
    control_type = ""
    name = ""
    automation_id = ""
    class_name = ""
    if ei is not None:
        control_type = str(getattr(ei, "control_type", "") or "")
        name = str(getattr(ei, "name", "") or "")
        automation_id = str(getattr(ei, "automation_id", "") or "")
        class_name = str(getattr(ei, "class_name", "") or "")
    return {
        "depth": depth,
        "control_type": control_type,
        "name": name,
        "name_len": len(name),
        "automation_id": automation_id,
        "class_name": class_name,
    }


def get_children(el):
    """获取直接子节点，失败返回空列表。"""
    children = getattr(el, "children", None)
    if callable(children):
        try:
            return list(children())
        except Exception:
            return []
    return []


def walk_tree(root):
    """广度优先遍历控件树，返回 (节点列表, 最大深度, 节点数, 是否被截断, 耗时ms)。"""
    nodes = []          # 元素为 (info, element)
    stack = [(root, 0)]
    max_depth = 0
    truncated = False
    start = time.time()
    while stack:
        if len(nodes) >= MAX_NODES:
            truncated = True
            break
        if time.time() - start >= TIME_BUDGET_SEC:
            truncated = True
            break
        el, depth = stack.pop()
        if depth > max_depth:
            max_depth = depth
        if depth > MAX_DEPTH:
            continue
        nodes.append((node_info(el, depth), el))
        for child in reversed(get_children(el)):
            stack.append((child, depth + 1))
    elapsed_ms = int((time.time() - start) * 1000)
    return nodes, max_depth, len(nodes), truncated, elapsed_ms


def find_wecom_windows(process_regex):
    """按进程名定位企微顶层窗口。"""
    from pywinauto import Desktop
    desktop = Desktop(backend="uia")
    try:
        all_windows = desktop.windows()
    except Exception:
        all_windows = []
    pattern = re.compile(process_regex, re.IGNORECASE)
    matched = []
    for w in all_windows:
        ei = get_element_info(w)
        if ei is None:
            continue
        pname = ""
        getter = getattr(ei, "process_name", None)
        if callable(getter):
            try:
                pname = str(getter() or "")
            except Exception:
                pname = ""
        elif isinstance(getter, str):
            pname = getter
        if pname and pattern.search(pname):
            matched.append(w)
    return matched


def main():
    parser = argparse.ArgumentParser(description="POC-02 UIA 读群消息（pywinauto 备选方案）")
    parser.add_argument("--run-index", type=int, choices=(1, 2), default=1,
                        help="1 = 第1次；2 = 第2次（冷启动后），输出文件名不同")
    parser.add_argument("--output-dir", default=os.path.dirname(os.path.abspath(__file__)))
    parser.add_argument("--redact-mode", choices=("alias", "full"), default="alias",
                        help="alias=文本脱敏后保留；full=文本仅留长度")
    parser.add_argument("--process-regex", default=r"^wxwork", help="企微进程名正则")
    args = parser.parse_args()

    out_file = os.path.join(args.output_dir, "POC-02-结果-第{}次.json".format(args.run_index))

    result = {
        "poc": "POC-02",
        "title": "UIA 读群消息（pywinauto 备选方案）",
        "runIndex": args.run_index,
        "generatedAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "env": {
            "pythonVersion": sys.version,
            "platform": sys.platform,
            "redactMode": args.redact_mode,
            "pywinautoAvailable": False,
        },
        "wecomProcessPattern": args.process_regex,
        "windows": [],
        "sessionListFound": False,
        "sessionItems": [],
        "messageAreaFound": False,
        "messageSamples": [],
        "inputBoxFound": False,
        "treeDepth": 0,
        "nodeCount": 0,
        "controlTypeCounts": {},
        "deepestSamples": [],
        "locatorRuleDesc": "",
        "verdict": "INCONCLUSIVE",
        "evidenceNote": "",
        "redactionNote": "会话名=GROUP_n、发送者=MEMBER_n、消息文本≤60 字符；手机号仅留后 4 位，邮箱/身份证占位。",
    }

    # --- 环境自检：pywinauto 是否可用 ---
    try:
        import pywinauto  # noqa: F401
        result["env"]["pywinautoAvailable"] = True
        try:
            from importlib.metadata import version as _v
            result["env"]["pywinautoVersion"] = _v("pywinauto")
        except Exception:
            result["env"]["pywinautoVersion"] = None
    except Exception as exc:
        result["verdict"] = "INCONCLUSIVE"
        result["evidenceNote"] = (
            "未安装 pywinauto（{0}）。本脚本为备选方案，需先执行 pip install pywinauto；"
            "若目标机不允许安装第三方包，请改用零安装主方案 POC-02-UIA读群消息.ps1。".format(exc)
        )
        with open(out_file, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print("pywinauto 不可用，已写出诊断结果：{0}".format(out_file))
        return 2

    try:
        windows = find_wecom_windows(args.process_regex)
    except Exception as exc:
        result["verdict"] = "INCONCLUSIVE"
        result["evidenceNote"] = "枚举顶层窗口失败：{0}".format(exc)
        with open(out_file, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        return 2

    print("[窗口] 匹配到的企微窗口数 = {0}".format(len(windows)))

    if not windows:
        result["verdict"] = "FAIL"
        result["evidenceNote"] = (
            "未找到企微窗口（进程名正则：{0}）。请确认企业微信 PC 客户端已人工登录且主窗口可见。"
            .format(args.process_regex)
        )
        with open(out_file, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print("未找到企微窗口：{0}".format(out_file))
        return 1

    # --- 遍历全部窗口的 UIA 树 ---
    all_nodes = []
    max_depth = 0
    total_nodes = 0
    truncated_any = False
    for w in windows:
        nodes, depth, count, truncated, _elapsed = walk_tree(w)
        all_nodes.extend(nodes)
        max_depth = max(max_depth, depth)
        total_nodes += count
        truncated_any = truncated_any or truncated
        win_info = node_info(w, 0)
        result["windows"].append({
            "name": "WINDOW_{0}".format(len(result["windows"]) + 1),  # 窗口标题可能含个人/群名，占位
            "nameLen": win_info["name_len"],
            "controlType": win_info["control_type"],
            "className": win_info["class_name"],
            "automationId": win_info["automation_id"],
            "nodeCount": count,
            "treeDepth": depth,
        })

    result["treeDepth"] = max_depth
    result["nodeCount"] = total_nodes

    # 控件类型分布（诊断用）
    counts = {}
    for info, _el in all_nodes:
        key = info["control_type"] or "UNKNOWN"
        counts[key] = counts.get(key, 0) + 1
    result["controlTypeCounts"] = dict(sorted(counts.items(), key=lambda kv: kv[1], reverse=True))

    # 最深层级采样（降级诊断）
    result["deepestSamples"] = [
        {
            "controlType": info["control_type"],
            "className": info["class_name"],
            "automationId": info["automation_id"],
            "nameLen": info["name_len"],
        }
        for info, _el in all_nodes if info["depth"] == max_depth
    ][:10]

    # --- 会话列表定位 ---
    list_items = [(i, e) for i, e in all_nodes
                  if i["control_type"] in ("ListItem", "TreeItem") and i["name_len"] > 0]
    if len(list_items) >= 2:
        result["sessionListFound"] = True
        for idx, (info, _el) in enumerate(list_items[:30], start=1):
            result["sessionItems"].append({
                "name": "GROUP_{0}".format(idx),
                "nameLen": info["name_len"],
                "controlType": info["control_type"],
                "automationId": info["automation_id"],
                "className": info["class_name"],
            })

    # --- 消息区定位 ---
    text_nodes = [(i, e) for i, e in all_nodes if i["control_type"] in ("Text", "Edit") and i["name_len"] > 0]
    edit_nodes = [i for i, _e in all_nodes if i["control_type"] == "Edit"]
    result["inputBoxFound"] = len(edit_nodes) > 0

    session_names = set(i["name"] for i, _e in list_items)
    msg_nodes = [(i, e) for i, e in text_nodes if i["control_type"] == "Text" and i["name"] not in session_names]

    member_alias = {}
    if msg_nodes:
        result["messageAreaFound"] = True
        for info, _el in msg_nodes[-10:]:
            raw = info["name"]
            safe = raw
            for key, alias in member_alias.items():
                safe = safe.replace(key, alias)
            for key in session_names:
                safe = safe.replace(key, "GROUP_x")
            if args.redact_mode == "full":
                safe = "<TEXT len={0}>".format(len(raw))
            else:
                safe = protect_text(safe, 60)
            result["messageSamples"].append({
                "text": safe,
                "textLen": len(raw),
                "sender": "",
                "time": "",
                "controlType": info["control_type"],
                "automationId": info["automation_id"],
            })

    # --- 定位规则固化 ---
    list_ct = list_items[0][0]["control_type"] if list_items else "NA"
    list_cls = list_items[0][0]["class_name"] if list_items else ""
    msg_cls = msg_nodes[0][0]["class_name"] if msg_nodes else ""
    edit_aid = edit_nodes[0]["automation_id"] if edit_nodes else ""
    result["locatorRuleDesc"] = (
        "sessionList=ControlType:{0}/ClassName:{1}(count={2}); "
        "messageText=ControlType:Text/ClassName:{3}(count={4}); "
        "inputBox=ControlType:Edit/AutomationId:{5}; treeDepth={6}; nodeCount={7}"
    ).format(list_ct, list_cls, len(list_items), msg_cls, len(msg_nodes), edit_aid,
             result["treeDepth"], result["nodeCount"])

    # --- 判定 ---
    if result["messageAreaFound"] and result["sessionListFound"]:
        result["verdict"] = "PASS"
        result["evidenceNote"] = (
            "同时取到消息文本与会话名，R2 具备可行性。按 ADR 通过判据还需两次冷启动后定位规则仍成立——"
            "请在第 2 次运行后比对两份 locatorRuleDesc / treeDepth / nodeCount。"
        )
    elif result["messageAreaFound"]:
        result["verdict"] = "INCONCLUSIVE"
        result["evidenceNote"] = "取到消息文本但未识别到会话列表控件，需结合 controlTypeCounts / deepestSamples 人工判定。"
    elif result["sessionListFound"]:
        result["verdict"] = "INCONCLUSIVE"
        result["evidenceNote"] = "只取到会话列表，未取到消息区文本（R2 典型降级形态）。可尝试打开有历史消息的群后重跑，或以 --force-renderer-accessibility 重启企微。"
    else:
        result["verdict"] = "FAIL"
        result["evidenceNote"] = "仅枚举到窗口壳，R2 不成立，转 ADR 第 21 章 Q7 决策。"

    if truncated_any:
        result["evidenceNote"] += " （遍历触达上限被截断，结果可能不完整。）"

    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print("==== 结论（第 {0} 次）====".format(args.run_index))
    print("verdict = {0}".format(result["verdict"]))
    print("sessionListFound={0} sessionItems={1} messageAreaFound={2} messageSamples={3} inputBoxFound={4}".format(
        result["sessionListFound"], len(result["sessionItems"]),
        result["messageAreaFound"], len(result["messageSamples"]), result["inputBoxFound"]))
    print("treeDepth={0} nodeCount={1}".format(result["treeDepth"], result["nodeCount"]))
    print("说明：{0}".format(result["evidenceNote"]))
    print("结果已写入：{0}".format(out_file))
    return 0


if __name__ == "__main__":
    sys.exit(main())
