package com.cowork.business.service;

import com.cowork.business.service.TokenService.IssuedToken;
import com.cowork.config.AuthProperties;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dto.Actor;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC01, UC23: nội dung access token JWT. Bộ ký JWT được giả lập. Viết theo {@code .claude/rules/unit-test.md}. */
public class TokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private JwtEncoder encoder;
    private TokenService service;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZONE);
        clock.set(NOW);
        encoder = mock(JwtEncoder.class);
        AuthProperties properties = new AuthProperties("secret", "cowork-test", Duration.ofHours(8), Duration.ofMinutes(5), 5,
                Duration.ofSeconds(60));
        service = new TokenService(encoder, properties, clock);
        when(encoder.encode(any())).thenReturn(Jwt.withTokenValue("signed-jwt").header("alg", "HS256").claim("sub", "x").build());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_issue_Customer_001() {
        // Token khách: sub "customer:7", role CUSTOMER, hạn 8 giờ, không có branchId.
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, displayName = {}, branchId = {}", actor, "Lê C", null);

        IssuedToken token = service.issue(actor, "Lê C", null);

        JwtClaimsSet claims = claims();
        log.output("value = {}, expiresAt = {}, subject = {}, roles = {}, issuer = {}, hasBranch = {}", token.value(),
                token.expiresAt(), claims.getSubject(), claims.getClaim("roles"), claims.getClaim("iss"),
                claims.hasClaim("branchId"));
        assertEquals("signed-jwt", token.value());
        assertEquals(NOW.atZone(ZONE).toInstant().plus(Duration.ofHours(8)), token.expiresAt());
        assertEquals("customer:7", claims.getSubject());
        assertEquals(List.of("CUSTOMER"), claims.getClaim("roles"));
        assertEquals("cowork-test", claims.getClaim("iss").toString());
        assertFalse(claims.hasClaim("branchId"));
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_issue_StaffWithBranch_002() {
        // Token nhân viên có chi nhánh: role theo vai trò, có branchId, uid, type, name.
        Actor actor = Actor.staff(5L, StaffRole.RECEPTIONIST);
        log.input("actor = {}, displayName = {}, branchId = {}", actor, "Lan", 1L);

        service.issue(actor, "Lan", 1L);

        JwtClaimsSet claims = claims();
        log.output("subject = {}, roles = {}, branchId = {}, uid = {}, type = {}, name = {}", claims.getSubject(),
                claims.getClaim("roles"), claims.getClaim("branchId"), claims.getClaim("uid"), claims.getClaim("type"),
                claims.getClaim("name"));
        assertEquals("staff:5", claims.getSubject());
        assertEquals(List.of("RECEPTIONIST"), claims.getClaim("roles"));
        assertEquals(Long.valueOf(1L), claims.getClaim("branchId"));
        assertEquals(Long.valueOf(5L), claims.getClaim("uid"));
        assertEquals("STAFF", claims.getClaim("type"));
        assertEquals("Lan", claims.getClaim("name"));
    }

    // Viewpoint: VP7
    @Test
    public void test_issue_NullDisplayName_003() {
        // Tên hiển thị null: Spring không cho claim null.
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, displayName = {}, branchId = {}", actor, null, null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> service.issue(actor, null, null));

        log.output("exception = {}", thrown.getClass().getSimpleName());
        assertEquals(IllegalArgumentException.class, thrown.getClass());
    }

    // Viewpoint: VP7
    @Test
    public void test_issue_NullActor_004() {
        // Actor null.
        Actor actor = null;
        log.input("actor = {}, displayName = {}, branchId = {}", actor, "Lê C", null);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.issue(actor, "Lê C", null));

        log.output("exception = {}", thrown.getClass().getSimpleName());
        assertEquals(NullPointerException.class, thrown.getClass());
        verify(encoder, never()).encode(any());
    }

    private JwtClaimsSet claims() {
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(captor.capture());
        return captor.getValue().getClaims();
    }
}
