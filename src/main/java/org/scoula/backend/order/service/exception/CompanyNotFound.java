package org.scoula.backend.order.service.exception;

import org.scoula.backend.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class CompanyNotFound extends BaseException {

    public CompanyNotFound() {
        super("존재하지 않는 정보입니다.", HttpStatus.NOT_FOUND);
    }
}
