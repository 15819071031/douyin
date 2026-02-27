package com.kai.douyin.service;


import com.kai.douyin.dal.dto.CommentInfo;
import com.kai.douyin.dal.entity.CommentTaskLog;
import com.kai.douyin.mapper.CommentTaskLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentTaskService {

    private final CommentTaskLogMapper commentTaskLogMapper;

    public void saveLog(CommentInfo comment, Integer status, String errorMsg) {
        CommentTaskLog taskLog = new CommentTaskLog();
        taskLog.setOriginalComment(comment.getContent());
        taskLog.setAiReply(comment.getReplyContent());
        taskLog.setStatus(status);
        taskLog.setErrorMsg(errorMsg);
        commentTaskLogMapper.insert(taskLog);
    }
}