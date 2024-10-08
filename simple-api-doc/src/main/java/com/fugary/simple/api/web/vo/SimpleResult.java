package com.fugary.simple.api.web.vo;

import com.fugary.simple.api.web.vo.query.SimplePage;
import com.fugary.simple.api.contants.SystemErrorConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Created on 2020/5/4 9:38 .<br>
 *
 * @author gary.fu
 */
@Getter
@Setter
@Builder(toBuilder = true)
public class SimpleResult<T> {

    /**
     * 响应码
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应的数据
     */
    private T resultData;

    /**
     * 分页数据
     */
    private SimplePage page;

    /**
     * 是否成功
     * @return
     */
    public boolean isSuccess(){
        return code == SystemErrorConstants.CODE_0;
    }
}
