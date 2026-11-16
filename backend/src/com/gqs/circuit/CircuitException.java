package com.gqs.circuit;

/**
 * 线路构建 / 执行相关的业务异常，用于向客户端返回友好的错误信息。
 */
public final class CircuitException extends RuntimeException {

    public CircuitException(String message) {
        super(message);
    }

    public CircuitException(String message, Throwable cause) {
        super(message, cause);
    }
}
