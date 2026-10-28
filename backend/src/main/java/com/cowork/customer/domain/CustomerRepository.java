package com.cowork.customer.domain;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByPhone(String phone);

    Customer save(Customer customer);
}
