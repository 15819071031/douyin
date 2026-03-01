package com.smart.ocr.service;

import com.smart.ocr.config.AppConfig;
import com.smart.ocr.entity.OcrHistory;
import com.smart.ocr.mapper.OcrHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 历史记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final OcrHistoryMapper historyMapper;
    private final AppConfig appConfig;

    /**
     * 保存识别历史
     */
    public void saveHistory(String text, String imagePath, String type) {
        try {
            OcrHistory history = new OcrHistory();
            history.setText(text);
            history.setImagePath(imagePath);
            history.setType(type);
            history.setCharCount(text != null ? text.length() : 0);
            history.setCreateTime(LocalDateTime.now());
            
            historyMapper.insert(history);
            log.debug("保存历史记录成功，ID: {}", history.getId());
            
            // 清理超出数量限制的历史记录
            cleanupExcessHistory();
        } catch (Exception e) {
            log.error("保存历史记录失败", e);
        }
    }

    /**
     * 获取历史记录列表
     */
    public List<OcrHistory> getHistoryList(int page, int size) {
        Page<OcrHistory> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OcrHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(OcrHistory::getCreateTime);
        
        Page<OcrHistory> result = historyMapper.selectPage(pageParam, wrapper);
        return result.getRecords();
    }

    /**
     * 搜索历史记录
     */
    public List<OcrHistory> searchHistory(String keyword, int page, int size) {
        Page<OcrHistory> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OcrHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(OcrHistory::getText, keyword)
               .orderByDesc(OcrHistory::getCreateTime);
        
        Page<OcrHistory> result = historyMapper.selectPage(pageParam, wrapper);
        return result.getRecords();
    }

    /**
     * 删除历史记录
     */
    public void deleteHistory(Long id) {
        historyMapper.deleteById(id);
        log.info("删除历史记录: {}", id);
    }

    /**
     * 清空所有历史记录
     */
    public void clearAllHistory() {
        historyMapper.delete(null);
        log.info("已清空所有历史记录");
    }

    /**
     * 获取历史记录总数
     */
    public long getHistoryCount() {
        return historyMapper.selectCount(null);
    }

    /**
     * 清理超出数量限制的历史记录
     */
    private void cleanupExcessHistory() {
        long count = getHistoryCount();
        int maxCount = appConfig.getMaxHistoryCount();
        
        if (count > maxCount) {
            // 删除最旧的记录
            int deleteCount = (int) (count - maxCount);
            LambdaQueryWrapper<OcrHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByAsc(OcrHistory::getCreateTime)
                   .last("LIMIT " + deleteCount);
            
            List<OcrHistory> toDelete = historyMapper.selectList(wrapper);
            for (OcrHistory history : toDelete) {
                historyMapper.deleteById(history.getId());
            }
            log.info("清理超出限制的历史记录: {} 条", deleteCount);
        }
    }

    /**
     * 清理过期历史记录
     */
    public void cleanupExpiredHistory() {
        int retentionDays = appConfig.getHistoryRetentionDays();
        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);
        
        LambdaQueryWrapper<OcrHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(OcrHistory::getCreateTime, expireTime);
        
        int deleted = historyMapper.delete(wrapper);
        if (deleted > 0) {
            log.info("清理过期历史记录: {} 条", deleted);
        }
    }
}
