package com.bankflow.customer.application.customer;

import com.bankflow.customer.domain.customer.Customer;
import com.bankflow.customer.domain.customer.CustomerRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultation et mise à jour du profil par son propriétaire. */
@Service
public class CustomerProfileService {

    private final CustomerRepository customers;
    private final Clock clock;

    CustomerProfileService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Customer getOwnProfile(UUID userId) {
        return customers.findByUserId(userId).orElseThrow(CustomerNotFoundException::new);
    }

    @Transactional
    public Customer updateOwnContact(UUID userId, ContactDetails details) {
        var customer = getOwnProfile(userId);
        customer.updateContact(
                details.firstName(),
                details.lastName(),
                details.phone(),
                details.address(),
                clock.instant());
        return customer;
    }

    /** Réservé au propriétaire et aux administrateurs ; les autres reçoivent « introuvable ». */
    @Transactional(readOnly = true)
    public Customer getCustomer(UUID customerId, CurrentUser user) {
        var customer = customers.findById(customerId).orElseThrow(CustomerNotFoundException::new);
        if (!user.admin() && !customer.getUserId().equals(user.userId())) {
            throw new CustomerNotFoundException();
        }
        return customer;
    }
}
