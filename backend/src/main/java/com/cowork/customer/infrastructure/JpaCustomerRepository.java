package com.cowork.customer.infrastructure;

import com.cowork.customer.domain.Customer;
import com.cowork.customer.domain.CustomerRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaCustomerRepository implements CustomerRepository {

    private final SpringDataCustomerRepository jpa;

    JpaCustomerRepository(SpringDataCustomerRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Customer> findByPhone(String phone) {
        return jpa.findByContactPhone(phone);
    }

    @Override
    public Customer save(Customer customer) {
        return jpa.save(customer);
    }
}
