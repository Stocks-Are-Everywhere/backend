package org.scoula.backend.member.exception;

public class AccountNotFound extends RuntimeException {
    public AccountNotFound() {
        super("존재하지 않는 계좌 정보입니다.");
    }
}
