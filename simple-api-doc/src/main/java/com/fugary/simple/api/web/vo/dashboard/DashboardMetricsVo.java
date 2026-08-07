package com.fugary.simple.api.web.vo.dashboard;

import lombok.Data;

import java.io.Serializable;

/**
 * Dashboard 指标数据
 *
 * @author gary.fu
 */
@Data
public class DashboardMetricsVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 项目总数
     */
    private Integer projectCount;

    /**
     * API接口/文档总数
     */
    private Integer apiCount;

    /**
     * 用户/成员总数
     */
    private Integer userCount;

    /**
     * 项目分组数
     */
    private Integer groupCount;

    /**
     * 分享总数
     */
    private Integer shareCount;

    /**
     * AI模拟数据/缓存总数
     */
    private Integer aiCacheCount;
}
