package com.cowork.customer.application;

import com.cowork.customer.domain.ContactInfo;
import com.cowork.customer.domain.Customer;
import com.cowork.customer.domain.CustomerRepository;
import com.cowork.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    /** Tìm khách theo số điện thoại; chưa có thì tạo mới (khách vãng lai, FR-AUTH-01). */
    public Customer registerOrUpdate(ContactInfo contact) {
        return customers.findByPhone(contact.phone())
                .map(existing -> {
                    existing.updateContact(contact);
                    return existing;
                })
                .orElseGet(() -> customers.save(Customer.register(contact, LocalDateTime.now(clock))));
    }

    @Transactional(readOnly = true)
    public ContactInfo contactOf(UUID customerId) {
        return customers.findById(customerId)
                .map(Customer::contact)
                .orElseThrow(() -> new NotFoundException("Khách hàng", customerId));
    }
}
