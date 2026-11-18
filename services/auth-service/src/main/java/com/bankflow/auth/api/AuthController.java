package com.bankflow.auth.api;

import com.bankflow.auth.api.dto.RegisterRequest;
import com.bankflow.auth.api.dto.UserResponse;
import com.bankflow.auth.application.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final UserMapper userMapper;

    AuthController(RegistrationService registrationService, UserMapper userMapper) {
        this.registrationService = registrationService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userMapper.toResponse(
                registrationService.register(request.email(), request.password()));
    }
}
