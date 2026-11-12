package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    /** Mã gửi gần nhất cho số điện thoại — chỉ mã mới nhất được dùng để xác thực. */
    Optional<OtpCode> findFirstByPhoneOrderByCreatedAtDescIdDesc(String phone);
}
