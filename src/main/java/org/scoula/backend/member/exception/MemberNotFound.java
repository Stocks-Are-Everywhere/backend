package org.scoula.backend.member.exception;

public class MemberNotFound extends RuntimeException {

    public MemberNotFound() {
        super("존재하지 않는 사용자입니다.");
    }
}
