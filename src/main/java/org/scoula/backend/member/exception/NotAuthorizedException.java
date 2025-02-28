package org.scoula.backend.member.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class NotAuthorizedException extends IllegalArgumentException{
    private final HttpStatus status;
    public NotAuthorizedException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }
}
