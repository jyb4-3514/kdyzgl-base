package com.qiujie.service.sync;

/**
 * 导出文件载体（文件名 + 字节内容）。
 * <p>
 * 为什么不让 Service 直接写 {@code HttpServletResponse}：文件响应头（Content-Disposition 的 RFC 5987 编码）
 * 属 Web 层关注点，Service 只产出「文件名 + 字节」，便于单测与复用。
 */
public record SyncExportFile(byte[] content, String filename) {
}
