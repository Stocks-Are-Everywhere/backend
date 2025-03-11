package org.scoula.backend.member.controller;

import java.io.IOException;

import org.scoula.backend.member.controller.response.LoginResponseDto;
import org.scoula.backend.member.service.GoogleOAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberAuthController {
	private final GoogleOAuthService googleOAuthService;

	@Value("${google.client.id}")
	private String googleClientId;

	@Value("${google.redirect.uri}")
	private String googleRedirectUri;

	@GetMapping("/google/login")
	public void redirectToGoogle(HttpServletResponse response) throws IOException {
		String googleLoginUrl = "https://accounts.google.com/o/oauth2/auth"
				+ "?client_id=" + googleClientId
				+ "&redirect_uri=" + googleRedirectUri
				+ "&response_type=code"
				+ "&scope=email%20profile";

		response.sendRedirect(googleLoginUrl);
	}

	@GetMapping("/google/callback")
	public ResponseEntity<LoginResponseDto> googleLogin(@RequestParam("code") String code,
			HttpServletResponse response) throws IOException {
		LoginResponseDto responseDto = googleOAuthService.googleLogin(code, response);
		return ResponseEntity.ok().body(responseDto);
	}
}
