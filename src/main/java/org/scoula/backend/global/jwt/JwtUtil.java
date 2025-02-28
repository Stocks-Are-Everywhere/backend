package org.scoula.backend.global.jwt;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "JwtUtil")
@RequiredArgsConstructor
@Component
public class JwtUtil {
	public static final String AUTHORIZATION_HEADER = "Authorization";
	public static final String BEARER_PREFIX = "Bearer ";
	private static final long TOKEN_TIME = 24 * 60 * 60 * 1000L; // 24 hours

	@Value("${jwt.secret.key}")
	private String secretKey;
	private Key key;
	private static final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

	@PostConstruct
	public void init() {
		byte[] bytes = Base64.getDecoder().decode(secretKey);
		key = Keys.hmacShaKeyFor(bytes);
	}

	public String createToken(String username) {
		Date now = new Date();
		return BEARER_PREFIX +
				Jwts.builder()
						.setSubject(username)
						.setExpiration(new Date(now.getTime() + TOKEN_TIME))
						.setIssuedAt(now)
						.signWith(key, signatureAlgorithm)
						.compact();
	}

	public String getJwtFromHeader(HttpServletRequest request) {
		String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
			return bearerToken.substring(7);
		}
		return null;
	}

	public boolean validateToken(String token) {
		try {
			Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
			return true;
		} catch (SecurityException | MalformedJwtException e) {
			log.error("Invalid JWT signature.");
		} catch (ExpiredJwtException e) {
			log.error("Expired JWT token.");
		} catch (UnsupportedJwtException e) {
			log.error("Unsupported JWT token.");
		} catch (IllegalArgumentException e) {
			log.error("Invalid JWT token.");
		}
		return false;
	}

	public Claims getUserInfoFromToken(String token) {
		token = cleanToken(token);
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
	}

	private String cleanToken(String token) {
		if (token == null) {
			return null;
		}
		token = token.trim();
		if (token.startsWith(BEARER_PREFIX)) {
			token = token.substring(7).trim();
		}
		return token;
	}
}
