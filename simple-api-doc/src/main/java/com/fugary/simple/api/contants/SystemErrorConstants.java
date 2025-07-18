package com.fugary.simple.api.contants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Created on 2020/5/4 9:53 .<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemErrorConstants {

    public static final int CODE_0 = 0; // 操作成功

    public static final int CODE_1 = 1; // 操作失败

    public static final int CODE_400 = 400; // 请求错误

    public static final int CODE_401 = 401; // 认证失败

    public static final int CODE_404 = 404; // 数据不存在

    public static final int CODE_403 = 403; // 没有权限访问

    public static final int CODE_500 = 500; // 服务器内部错误

    public static final int CODE_1001 = 1001; // 数据已经存在
    /**
     * 数据没有变化
     */
    public static final int CODE_2000 = 2000;
    /**
     * 用户不存在或密码不正确
     */
    public static final int CODE_2001 = 2001;
    /**
     * 上传文件不能为空
     */
    public static final int CODE_2002 = 2002;
    /**
     * 上传文件解析失败
     */
    public static final int CODE_2003 = 2003;
    /**
     * 不支持的导入数据类型
     */
    public static final int CODE_2004 = 2004;
    /**
     * URL导入解析失败
     */
    public static final int CODE_2005 = 2005;
    /**
     * API内容解析失败
     */
    public static final int CODE_2006 = 2006;
    /**
     * 根文件夹不能删除
     */
    public static final int CODE_2007 = 2007;
    /**
     * 文档分享已过期
     */
    public static final int CODE_2008 = 2008;
    /**
     * URL数据下载失败
     */
    public static final int CODE_2009 = 2009;
    /**
     * 多个导入文档可能存在相同Component，不能一起导出
     */
    public static final int CODE_2010 = 2010;
    /**
     * 没有找到需要导出的文档或API
     */
    public static final int CODE_2011 = 2011;
}
