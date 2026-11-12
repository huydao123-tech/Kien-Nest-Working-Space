package com.cowork.presentation.controller;

import com.cowork.business.service.CustomerService;
import com.cowork.business.service.StaffService;
import com.cowork.dto.PeopleDtos.CustomerRequest;
import com.cowork.dto.PeopleDtos.CustomerResponse;
import com.cowork.dto.PeopleDtos.StaffRequest;
import com.cowork.dto.PeopleDtos.StaffResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** UC23: CRUD nhân viên (ADMIN) và hồ sơ khách hàng (nhân viên quầy). */
@RestController
@RequestMapping("/api/admin")
class PeopleController {

    private final StaffService staffService;
    private final CustomerService customerService;

    PeopleController(StaffService staffService, CustomerService customerService) {
        this.staffService = staffService;
        this.customerService = customerService;
    }

    // ---------------- staff ----------------

    @GetMapping("/staff")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    List<StaffResponse> listStaff() {
        return staffService.list().stream().map(StaffResponse::from).toList();
    }

    @GetMapping("/staff/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    StaffResponse getStaff(@PathVariable Long id) {
        return StaffResponse.from(staffService.get(id));
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN)
    StaffResponse createStaff(@Valid @RequestBody StaffRequest request) {
        return StaffResponse.from(staffService.create(request));
    }

    @PutMapping("/staff/{id}")
    @PreAuthorize(Roles.ADMIN)
    StaffResponse updateStaff(@PathVariable Long id, @Valid @RequestBody StaffRequest request, @AuthenticationPrincipal Jwt jwt) {
        return StaffResponse.from(staffService.update(id, request, Roles.actorOf(jwt)));
    }

    @DeleteMapping("/staff/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN)
    void deleteStaff(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        staffService.delete(id, Roles.actorOf(jwt));
    }

    // ---------------- customer ----------------

    @GetMapping("/customers")
    @PreAuthorize(Roles.FRONT_DESK)
    List<CustomerResponse> listCustomers() {
        return customerService.list().stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize(Roles.FRONT_DESK)
    CustomerResponse getCustomer(@PathVariable Long id) {
        return CustomerResponse.from(customerService.get(id));
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.FRONT_DESK)
    CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(customerService.create(request));
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize(Roles.FRONT_DESK)
    CustomerResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(customerService.update(id, request));
    }

    @DeleteMapping("/customers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN)
    void deleteCustomer(@PathVariable Long id) {
        customerService.delete(id);
    }
}
