package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Query("select c from Customer c where c.contact.phone = :phone")
    Optional<Customer> findByPhone(@Param("phone") String phone);
}
