package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.config.AuthProperties;
import com.cowork.dataaccess.entity.OtpCode;
import com.cowork.dataaccess.repository.OtpCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * UC01, FR-AUTH-02: mã OTP xác thực số điện thoại.
 * <ul>
 *   <li>Mã 6 chữ số, hiệu lực {@code auth.otp-ttl} (5 phút); chỉ mã gửi gần nhất của số đó được dùng.</li>
 *   <li>Gửi lại phải cách lần trước ít nhất {@code auth.otp-resend-cooldown} (60 giây).</li>
 *   <li>Sai quá {@code auth.otp-max-attempts} (5) lần thì mã bị vô hiệu, phải xin mã mới.</li>
 *   <li>Chỉ lưu mã đã băm (BCrypt); mã đã dùng thì không dùng lại được.</li>
 * </ul>
 */
@Service
@Transactional
public class OtpService {

    private final OtpCodeRepository otpCodes;
    private final OtpGenerator generator;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final AuthProperties properties;
    private final Clock clock;

    public OtpService(OtpCodeRepository otpCodes, OtpGenerator generator, PasswordEncoder encoder,
                      NotificationService notifications, AuthProperties properties, Clock clock) {
        this.otpCodes = otpCodes;
        this.generator = generator;
        this.encoder = encoder;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    /** Tạo mã mới và gửi SMS. Trả về thời điểm mã hết hạn. */
    public LocalDateTime send(String phone) {
        CustomerService.requireValidPhone(phone);
        LocalDateTime now = LocalDateTime.now(clock);
        otpCodes.findFirstByPhoneOrderByCreatedAtDescIdDesc(phone).ifPresent(last -> {
            LocalDateTime allowedAt = last.getCreatedAt().plus(properties.otpResendCooldown());
            if (now.isBefore(allowedAt)) {
                throw new DomainException("OTP_RESEND_TOO_SOON", "Vui lòng chờ tới " + allowedAt.toLocalTime() + " để gửi lại mã");
            }
        });
        String code = generator.next();
        OtpCode otp = otpCodes.save(new OtpCode(phone, encoder.encode(code), now, now.plus(properties.otpTtl())));
        notifications.otp(phone, code);
        return otp.getExpiresAt();
    }

    /**
     * Kiểm tra mã của số điện thoại; đúng thì đánh dấu đã dùng.
     * Chạy trong transaction riêng và không rollback khi mã sai, để số lần nhập sai vẫn được ghi lại
     * dù nghiệp vụ gọi nó (đăng ký, đăng nhập) báo lỗi.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DomainException.class)
    public void verify(String phone, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        OtpCode otp = otpCodes.findFirstByPhoneOrderByCreatedAtDescIdDesc(phone)
                .orElseThrow(() -> new DomainException("OTP_INVALID", "Mã OTP không đúng"));
        if (otp.getConsumedAt() != null) {
            throw new DomainException("OTP_INVALID", "Mã OTP đã được sử dụng, vui lòng xin mã mới");
        }
        if (!now.isBefore(otp.getExpiresAt())) {
            throw new DomainException("OTP_EXPIRED", "Mã OTP đã hết hạn, vui lòng xin mã mới");
        }
        if (otp.getAttempts() >= properties.otpMaxAttempts()) {
            throw new DomainException("OTP_TOO_MANY_ATTEMPTS", "Nhập sai quá số lần cho phép, vui lòng xin mã mới");
        }
        if (code == null || !encoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpCodes.save(otp);
            throw new DomainException("OTP_INVALID", "Mã OTP không đúng");
        }
        otp.setConsumedAt(now);
        otpCodes.save(otp);
    }
}
