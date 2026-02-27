package com.kai.douyin.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comment_task_log")
public class CommentTaskLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String videoDesc;
    private String originalComment;
    private String aiReply;
    private Integer status;  // 0-待处理 1-已回复 2-失败
    private String errorMsg;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}