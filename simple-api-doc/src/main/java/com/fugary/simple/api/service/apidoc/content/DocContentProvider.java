package com.fugary.simple.api.service.apidoc.content;

import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.imports.DocSourceData;

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
    SimpleResult<DocSourceData> getContent(T source);
}
