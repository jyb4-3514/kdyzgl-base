package com.qiujie.util.excel;

import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.Map;

/**
 * 员工导入模板写处理器：
 * 1. 全列默认文本格式——避免手机号/日期被 Excel 自动转成数值（科学计数法）；
 * 2. 表头单元格挂批注——填写说明不放示例行（防误导入产生脏数据，api.md 4.3.8）。
 * 注意：批注需完整 POI 支持，写出时须开启 inMemory(true)。
 */
public class ImportTemplateStyleHandler implements SheetWriteHandler, CellWriteHandler {

    private static final int COLUMN_COUNT = 8;

    /** 列索引 → 批注文案 */
    private final Map<Integer, String> headerComments;

    public ImportTemplateStyleHandler(Map<Integer, String> headerComments) {
        this.headerComments = headerComments;
    }

    /** sheet 创建后：全列设置文本格式 */
    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Sheet sheet = writeSheetHolder.getSheet();
        CellStyle textStyle = sheet.getWorkbook().createCellStyle();
        textStyle.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat("@"));
        for (int i = 0; i < COLUMN_COUNT; i++) {
            sheet.setDefaultColumnStyle(i, textStyle);
        }
    }

    /** 表头单元格写完后：挂批注 */
    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        if (!Boolean.TRUE.equals(context.getHead())) {
            return;
        }
        Cell cell = context.getCell();
        if (cell == null) {
            return;
        }
        String text = headerComments.get(cell.getColumnIndex());
        if (text == null) {
            return;
        }
        Sheet sheet = cell.getSheet();
        CreationHelper helper = sheet.getWorkbook().getCreationHelper();
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setRow1(cell.getRow().getRowNum());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow2(cell.getRow().getRowNum() + 4);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(helper.createRichTextString(text));
        cell.setCellComment(comment);
    }
}
