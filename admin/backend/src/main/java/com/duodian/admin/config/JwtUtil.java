package com.duodian.admin.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    // Base64-encoded random key for HS256
    private static final String SECRET_BASE64 = "YmxhY2tib3gtYWRtaW4tanVzdC1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLTIwMjQ=";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            Base64.getDecoder().decode(SECRET_BASE64));
    private static final long EXPIRATION_DAYS = 7;

    public String generateToken(Long userId, String phone) {
        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_DAYS, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(SECRET_KEY)
                .compact();
    }

    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public String extractPhone(String token) {
        Claims claims = parseToken(token);
        return claims.get("phone", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (UnsupportedJwtException e) {
            return false;
        } catch (MalformedJwtException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
