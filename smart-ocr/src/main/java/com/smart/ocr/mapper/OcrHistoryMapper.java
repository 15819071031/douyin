package com.smart.ocr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.ocr.entity.OcrHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * OCR历史记录Mapper
 */
@Mapper
public interface OcrHistoryMapper extends BaseMapper<OcrHistory> {
}
