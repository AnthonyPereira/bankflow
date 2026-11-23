package com.bankflow.customer.application.customer;

import com.bankflow.customer.domain.customer.Customer;
import com.bankflow.customer.domain.customer.CustomerRepository;
import com.bankflow.customer.domain.customer.CustomerStatus;
import com.bankflow.customer.domain.customer.CustomerUpdated;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opérations réservées aux administrateurs : recherche et changement de statut. */
@Service
public class CustomerAdminService {

    private final CustomerRepository customers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CustomerAdminService(
            CustomerRepository customers, ApplicationEventPublisher events, Clock clock) {
        this.customers = customers;
        this.events = events;
        this.clock = clock;
    }

    /** Les plus récents d'abord ; {@code q} cherche dans l'email, le prénom et le nom. */
    @Transactional(readOnly = true)
    public Page<Customer> searchCustomers(
            int page, int size, CustomerStatus status, String searchText) {
        var sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        return customers.findAll(
                createSearchSpecification(status, searchText), PageRequest.of(page, size, sort));
    }

    /**
     * Change le statut. {@code CustomerUpdated} n'est publié que si le statut change réellement.
     *
     * @throws com.bankflow.customer.domain.customer.DemoCustomerCannotBeSuspendedException pour un
     *     client de démonstration
     */
    @Transactional
    public Customer changeStatus(UUID customerId, CustomerStatus newStatus) {
        var customer = customers.findById(customerId).orElseThrow(CustomerNotFoundException::new);
        var previousStatus = customer.getStatus();
        var now = clock.instant();
        if (newStatus == CustomerStatus.SUSPENDED) {
            customer.suspend(now);
        } else {
            customer.activate(now);
        }
        if (customer.getStatus() != previousStatus) {
            customers.save(customer);
            events.publishEvent(CustomerUpdated.of(customer, now));
        }
        return customer;
    }

    private static Specification<Customer> createSearchSpecification(
            CustomerStatus status, String searchText) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (searchText != null && !searchText.isBlank()) {
                var pattern = "%" + escapeLike(searchText.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(
                        builder.or(
                                builder.like(builder.lower(root.get("email")), pattern, '\\'),
                                builder.like(builder.lower(root.get("firstName")), pattern, '\\'),
                                builder.like(builder.lower(root.get("lastName")), pattern, '\\')));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Un {@code %} ou un {@code _} saisi par l'administrateur est cherché tel quel. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
