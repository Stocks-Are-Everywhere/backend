package org.scoula.backend.mock;

import org.scoula.backend.global.security.UserDetailsImpl;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.MemberRoleEnum;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockUserDetailsSecurityContextFactory implements WithSecurityContextFactory<WithMockUserDetails> {

    @Override
    public SecurityContext createSecurityContext(WithMockUserDetails annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UserDetailsImpl userDetails = new UserDetailsImpl(
                Member.builder()
                        .googleId("google")
                        .email(annotation.email())
                        .username(annotation.username())
                        .role(MemberRoleEnum.USER)
                        .build()
        );

        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken
                = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        context.setAuthentication(usernamePasswordAuthenticationToken);
        return context;
    }
}
