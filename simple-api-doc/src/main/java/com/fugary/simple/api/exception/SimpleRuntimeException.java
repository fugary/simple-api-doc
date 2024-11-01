package com.fugary.simple.api.exception;

import lombok.Data;

/**
 * Create date 2024/11/1<br>
 *
 * @author gary.fu
 */
@Data
public class SimpleRuntimeException extends RuntimeException {

    private Integer code;

    public SimpleRuntimeException(Integer code) {
        super();
        this.code = code;
    }

    public SimpleRuntimeException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public SimpleRuntimeException(Integer code, Throwable cause) {
        super(cause);
        this.code = code;
    }

    public SimpleRuntimeException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
