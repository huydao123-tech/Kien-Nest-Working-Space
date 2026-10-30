package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.ContactInfo;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/** Khách hàng, định danh bằng số điện thoại. */
@Service
@Transactional
public class CustomerService {

    private static final Pattern VN_PHONE = Pattern.compile("^(0|\\+84)\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Tìm khách theo số điện thoại; chưa có thì tạo mới (khách vãng lai, FR-AUTH-01).
     * Khách cũ được cập nhật họ tên / email theo lần đặt mới nhất; email bỏ trống thì giữ email cũ.
     */
    public Customer registerOrUpdate(ContactInfo contact) {
        validate(contact);
        return customers.findByPhone(contact.phone())
                .map(existing -> {
                    String email = contact.hasEmail() ? contact.email() : existing.getContact().email();
                    existing.setContact(new ContactInfo(contact.fullName(), contact.phone(), email));
                    return existing;
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setId(UUID.randomUUID());
                    customer.setContact(contact);
                    customer.setCreatedAt(LocalDateTime.now(clock));
                    return customers.save(customer);
                });
    }

    @Transactional(readOnly = true)
    public ContactInfo contactOf(UUID customerId) {
        return customers.findById(customerId)
                .map(Customer::getContact)
                .orElseThrow(() -> new NotFoundException("Khách hàng", customerId));
    }

    private static void validate(ContactInfo contact) {
        if (contact.fullName() == null || contact.fullName().isBlank()) {
            throw new DomainException("INVALID_CONTACT", "Họ tên là bắt buộc");
        }
        if (contact.phone() == null || !VN_PHONE.matcher(contact.phone()).matches()) {
            throw new DomainException("INVALID_CONTACT", "Số điện thoại không hợp lệ");
        }
        if (contact.hasEmail() && !EMAIL.matcher(contact.email()).matches()) {
            throw new DomainException("INVALID_CONTACT", "Email không hợp lệ");
        }
    }
}
