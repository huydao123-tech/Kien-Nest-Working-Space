package com.cowork.presentation.controller;

import com.cowork.business.service.RefundService;
import com.cowork.dataaccess.enums.RefundStatus;
import com.cowork.dto.RefundDtos.RefundResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** UC25, FR-NOTI-04: quản lý xem yêu cầu hoàn tiền và xác nhận đã hoàn xong (ticket notification.md). */
@RestController
@RequestMapping("/api/refunds")
class RefundController {

    private final RefundService refundService;

    RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    @PreAuthorize(Roles.ADMIN_MANAGER)
    List<RefundResponse> list(@RequestParam(defaultValue = "REQUESTED") RefundStatus status) {
        return refundService.list(status);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    RefundResponse complete(@PathVariable Long id) {
        return refundService.complete(id);
    }
}
