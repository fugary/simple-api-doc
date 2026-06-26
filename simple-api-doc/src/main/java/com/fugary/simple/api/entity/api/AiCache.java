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
}
