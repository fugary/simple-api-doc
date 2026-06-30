package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 生成数据缓存实体
 */
@Data
@TableName("t_ai_cache")
public class AiCache {
    /**
     * SHA256哈希主键
     */
    @TableId
    private String cacheKey;

    /**
     * AI生成的JSON样本数据
     */
    private String cacheValue;

    /**
     * 使用的AI模型名称
     */
    private String modelName;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 状态（0:处理中, 1:成功, 2:失败）
     */
    private Integer status;

    /**
     * 生成耗费时间（毫秒）
     */
    private Long costTime;

    /**
     * 提示词
     */
    private String prompt;

    /**
     * 项目ID
     */
    private String projectId;

    /**
     * 文档ID
     */
    private String docId;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 操作用户
     */
    private String userName;

    /**
     * 缓存类型/任务类型
     */
    private String cacheType;

    /**
     * 消耗的 Prompt Token 数
     */
    private Integer promptTokens;

    /**
     * 消耗的 Completion Token 数
     */
    private Integer completionTokens;

    /**
     * 消耗的总 Token 数
     */
    private Integer totalTokens;

    /**
     * 原始大模型响应内容
     */
    private String rawResponse;

    /**
     * 更新/完成时间
     */
    private Date updatedAt;

    /**
     * 客户端请求 IP
     */
    private String clientIp;
}
