package com.betedge.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.betedge.auth")
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ProblemDetail handleInvalidGoogleToken(InvalidGoogleTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid Google token");
    }

    @ExceptionHandler(GoogleAccountConflictException.class)
    public ProblemDetail handleGoogleAccountConflict(GoogleAccountConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Esta cuenta usa otro método de acceso.");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    @ExceptionHandler(BootstrapNotAllowedException.class)
    public ProblemDetail handleBootstrapNotAllowed(BootstrapNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Forbidden");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
