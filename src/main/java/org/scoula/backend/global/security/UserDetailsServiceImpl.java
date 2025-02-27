package org.scoula.backend.global.security;

import lombok.RequiredArgsConstructor;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.repository.MemberJpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final MemberJpaRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Member> member = memberRepository.findByUsername(username);
        return member.map(UserDetailsImpl::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public UserDetails loadUserByGoogleId(String googleId) throws UsernameNotFoundException {
        Optional<Member> member = memberRepository.findByGoogleId(googleId);
        return member.map(UserDetailsImpl::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with Google ID: " + googleId));
    }
}
