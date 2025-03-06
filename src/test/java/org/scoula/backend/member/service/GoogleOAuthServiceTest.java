package org.scoula.backend.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.scoula.backend.global.jwt.JwtUtil;
import org.scoula.backend.member.controller.response.LoginResponseDto;
import org.scoula.backend.member.domain.Account;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.repository.impls.AccountRepositoryImpl;
import org.scoula.backend.member.repository.impls.MemberRepositoryImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @InjectMocks
    private GoogleOAuthService googleOAuthService;

    @Mock
    private MemberRepositoryImpl memberRepository;

	@Mock
	private AccountRepositoryImpl accountRepository;

	@Mock
    private JwtUtil jwtUtil;

	@Mock
	private HttpServletResponse response;

	@Mock
    private RestTemplate restTemplate;  // RestTemplate를 테스트 클래스에서 모킹

	private final String email = "test@example.com";

    @BeforeEach
    void setUp() {
        // 필수 필드 값 설정
        ReflectionTestUtils.setField(googleOAuthService, "tokenUri", "https://test.com/token");
        ReflectionTestUtils.setField(googleOAuthService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(googleOAuthService, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(googleOAuthService, "redirectUri", "http://localhost:8080/callback");
		ReflectionTestUtils.setField(googleOAuthService, "userInfoUri", "https://test.com/userinfo");
     
        // 1. Access Token 응답 모킹
        JsonNode tokenResponse = mock(JsonNode.class);
        when(tokenResponse.get("access_token")).thenReturn(new TextNode("mocked_access_token"));
        when(restTemplate.postForEntity(
            any(URI.class),
            isNull(),
            eq(JsonNode.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // 2. User Info 응답 모킹
        JsonNode userInfoResponse = mock(JsonNode.class);
        when(userInfoResponse.get("email")).thenReturn(new TextNode(email));
        when(userInfoResponse.get("sub")).thenReturn(new TextNode("123456789"));

        // exchange 메서드 모킹
        when(restTemplate.exchange(
            anyString(),                // userInfoUri
            eq(HttpMethod.GET),        // HTTP 메서드
            argThat(request -> {       // HttpEntity 매칭
                HttpHeaders headers = request.getHeaders();
                return headers.containsKey("Authorization") &&
                    headers.getFirst("Authorization").startsWith("Bearer ");
            }),
            eq(JsonNode.class)         // 응답 타입
        )).thenReturn(new ResponseEntity<>(userInfoResponse, HttpStatus.OK));
    
    }

    @Test
	@DisplayName("Create account when new user created")
    void whenNewUserCreated_thenAccountShouldBeCreated() throws IOException {
		// given
		String username = email.split("@")[0];
		BigDecimal expectedBalance = new BigDecimal(100000000);

		// Member 저장 모킹
		when(memberRepository.findByEmail(anyString())).thenReturn(Optional.empty());
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
			Member savedMember = invocation.getArgument(0);  // 저장하려는 Member 객체
			ReflectionTestUtils.setField(savedMember, "id", 1L);  // ID 설정
			return savedMember;  // 저장된 Member 반환
		});

		// when
		LoginResponseDto result = googleOAuthService.googleLogin("test-code", response);

		// then
		assertThat(result.getUsername()).isEqualTo(username);
		assertThat(result.getBalance()).isEqualTo(expectedBalance);
	}
}
