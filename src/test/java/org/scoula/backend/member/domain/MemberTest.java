package org.scoula.backend.member.domain;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.member.repository.AccountJpaRepository;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
import org.springframework.boot.test.context.SpringBootTest;


class MemberTest {

    @Test
    @DisplayName("Successfully create account with initial balance")
    void successfullyCreateAccount() {
        // given
        Member member = Member.builder()
                .googleId("123456")
                .email("test@example.com")
                .role(MemberRoleEnum.USER)
                .build();

        // when
        member.createAccount();

        // then
        assertThat(member.getAccount())
                .isNotNull()
                .extracting(Account::getMember)
                .isEqualTo(member);

        assertThat(member.getAccount().getBalance())
                .isEqualTo(new BigDecimal("100000000"));
    }

	@Test
    @DisplayName("Create account with proper bidirectional relationship")
    void createAccountWithProperBidirectionalRelationship() {
        // given
        Member member = Member.builder()
                .googleId("123456")
                .email("test@example.com")
                .role(MemberRoleEnum.USER)
                .build();

        // when
        member.createAccount();

        // then
        Account createdAccount = member.getAccount();
        assertThat(createdAccount.getMember())
                .isSameAs(member);  // 양방향 관계 검증
    }

}
