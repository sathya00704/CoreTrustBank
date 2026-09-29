package com.coretrust.bank.security;

import com.coretrust.bank.config.JwtProperties;
import com.coretrust.bank.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    //Constructor: builds the signing key from the secret, and fails at startup if the secret is too weak.
    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // Throws WeakKeyException at startup if the secret is shorter than 256 bits
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    //creates and signs a JWT containing the user id, role, issuer, issue time, expiry, and a unique id.
    public String generateAccessToken(Long userId, Role role, Instant expiresAt) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(properties.issuer())
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /** Verifies signature, expiry and issuer; throws JwtException if any check fails. */
    //Verifies the signature, expiry, and issuer, then returns the user id.
    //It throws if the token is tampered with or expired.
    public Long extractUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}