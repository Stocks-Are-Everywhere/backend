package org.scoula.backend.member.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class MemberNotFoundException extends BaseException {

    public MemberNotFoundException() {
        super("존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND);
    }
}
