package com.cowork.business.service;

import com.cowork.config.AuthProperties;
import com.cowork.dto.Actor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * UC01, UC23: phát hành access token JWT (HS256) sau khi đăng nhập thành công.
 * Token chứa: {@code sub} = "customer:{id}" / "staff:{id}", {@code uid}, {@code type}, {@code roles}, {@code name}
 * và {@code branchId} (nhân viên thuộc chi nhánh).
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(Actor actor, String displayName, Long branchId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwtIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(actor.type().name().toLowerCase() + ":" + actor.id())
                .claim("uid", actor.id())
                .claim("type", actor.type().name())
                .claim("roles", List.of(actor.roleName()))
                .claim("name", displayName);
        if (branchId != null) {
            claims.claim("branchId", branchId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
