package org.scoula.backend.member.controller.response;

import java.math.BigDecimal;

import lombok.Getter;

@Getter
public class LoginResponseDto {
    private final Long userId;
    private final String username;
    private final BigDecimal balance;

    public LoginResponseDto(Long userId, String username, BigDecimal balance) {
        this.userId = userId;
        this.username = username;
        this.balance = balance;
    }
}