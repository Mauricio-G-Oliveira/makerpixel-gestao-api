package com.makerpixel.contabil.reforma;

/**
 * Exceção lançada quando a Calculadora RTC oficial está indisponível
 * e o fallback local está desativado (comportamento padrão de produção).
 */
public class CalculadoraRtcIndisponivelException extends RuntimeException {

    public CalculadoraRtcIndisponivelException(String message) {
        super(message);
    }

    public CalculadoraRtcIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
