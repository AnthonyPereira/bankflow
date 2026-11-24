package com.bankflow.customer.api.customer;

import com.bankflow.customer.api.customer.dto.CustomerResponse;
import com.bankflow.customer.api.customer.dto.PageResponse;
import com.bankflow.customer.api.customer.dto.UpdateContactRequest;
import com.bankflow.customer.api.customer.dto.UpdateStatusRequest;
import com.bankflow.customer.api.error.InvalidTokenException;
import com.bankflow.customer.application.customer.CustomerAdminService;
import com.bankflow.customer.application.customer.CustomerProfileService;
import com.bankflow.customer.application.customer.model.CurrentUser;
import com.bankflow.customer.domain.customer.CustomerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
class CustomerController {

    private final CustomerProfileService profileService;
    private final CustomerAdminService adminService;

    CustomerController(CustomerProfileService profileService, CustomerAdminService adminService) {
        this.profileService = profileService;
        this.adminService = adminService;
    }

    @GetMapping("/me")
    CustomerResponse getMyProfile(JwtAuthenticationToken authentication) {
        return CustomerResponse.createFrom(
                profileService.getOwnProfile(createCurrentUser(authentication).userId()));
    }

    @PutMapping("/me")
    CustomerResponse updateMyProfile(
            JwtAuthenticationToken authentication, @Valid @RequestBody UpdateContactRequest body) {
        return CustomerResponse.createFrom(
                profileService.updateOwnContact(
                        createCurrentUser(authentication).userId(), body.createContactDetails()));
    }

    @GetMapping("/{id}")
    CustomerResponse getCustomer(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        return CustomerResponse.createFrom(
                profileService.getCustomer(id, createCurrentUser(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    PageResponse<CustomerResponse> listCustomers(
            JwtAuthenticationToken authentication,
            @RequestParam(defaultValue = "0")
                    @Min(value = 0, message = "La page doit être positive")
                    int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "La taille doit être comprise entre 1 et 100")
                    @Max(value = 100, message = "La taille doit être comprise entre 1 et 100")
                    int size,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) @Size(max = 100, message = "100 caractères au maximum")
                    String q) {
        return PageResponse.createFrom(
                adminService
                        .searchCustomers(
                                page,
                                size,
                                status,
                                q,
                                createCurrentUser(authentication).isRestrictedToDemo())
                        .map(CustomerResponse::createFrom));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    CustomerResponse updateStatus(
            JwtAuthenticationToken authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest body) {
        return CustomerResponse.createFrom(
                adminService.changeStatus(
                        id, body.status(), createCurrentUser(authentication).isRestrictedToDemo()));
    }

    private static CurrentUser createCurrentUser(JwtAuthenticationToken authentication) {
        var admin =
                authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        var demo = Boolean.TRUE.equals(authentication.getToken().getClaimAsBoolean("demo"));
        return new CurrentUser(parseUserId(authentication.getToken().getSubject()), admin, demo);
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidTokenException();
        }
    }
}
