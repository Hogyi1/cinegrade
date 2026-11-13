// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.domain.exceptions;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;

/**
 * A failed call to the web platform. Check {@link #getErrorCode()}:
 * {@code SOCIAL_NOT_LOGGED_IN} means the session is gone (sign in again),
 * {@code SOCIAL_OFFLINE} means no connection, the others are server-side problems.
 */
public class SocialException extends CineGradeException {

    public SocialException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public SocialException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }

    public boolean isNotLoggedIn() {
        return getErrorCode() == ErrorCode.SOCIAL_NOT_LOGGED_IN;
    }
}
