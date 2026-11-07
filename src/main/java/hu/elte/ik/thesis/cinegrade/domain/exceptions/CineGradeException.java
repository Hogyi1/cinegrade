package hu.elte.ik.thesis.cinegrade.domain.exceptions;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.enums.Severity;
import lombok.Getter;

public class CineGradeException extends RuntimeException {

    private final ErrorCode ERROR_CODE;
    private final Severity SEVERITY;
    @Getter
    private Throwable cause = null;

    public CineGradeException(ErrorCode errorCode) {
        super(errorCode.format());
        this.ERROR_CODE = errorCode;
        this.SEVERITY = errorCode.getSeverity();
    }

    public CineGradeException(ErrorCode errorCode, Object... args){
        super(errorCode.format(args));
        this.ERROR_CODE = errorCode;
        this.SEVERITY = errorCode.getSeverity();
    }

    public CineGradeException(ErrorCode errorCode, Throwable cause, Object... args){
        super(errorCode.format(args), cause);
        this.ERROR_CODE = errorCode;
        this.SEVERITY = errorCode.getSeverity();
        this.cause = cause;
    }

    public ErrorCode getErrorCode() {
        return ERROR_CODE;
    }

    public Severity getSeverity() {
        return SEVERITY;
    }

    public String getErrorMessage() {
        return getMessage() != null ? getMessage() : ERROR_CODE.getMessage();
    }

    public int getErrorCodeValue() {
        return ERROR_CODE.getCode();
    }
}
