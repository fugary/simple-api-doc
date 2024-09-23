package com.fugary.simple.api.service.apidoc.content;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface DocContentProvider<T> {
    /**
     * 获取Content
     *
     * @param source
     * @return
     */
    String getContent(T source);
}
