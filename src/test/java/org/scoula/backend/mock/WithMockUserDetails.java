package org.scoula.backend.mock;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockUserDetailsSecurityContextFactory.class)
public @interface WithMockUserDetails {
    String userId() default "1";
    String email() default "1";
    String username() default "email@google.com";
    String role() default "ROLE_USER";
}
