package org.scoula.backend.member.controller.response;

import lombok.Getter;

@Getter
public class LoginResponseDto {
    private final Long userId;
    private final String username;

    public LoginResponseDto(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }
}