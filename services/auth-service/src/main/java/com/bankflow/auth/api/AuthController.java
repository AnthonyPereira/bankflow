package com.bankflow.auth.api;

import com.bankflow.auth.api.dto.LoginRequest;
import com.bankflow.auth.api.dto.RegisterRequest;
import com.bankflow.auth.api.dto.TokenResponse;
import com.bankflow.auth.api.dto.UserResponse;
import com.bankflow.auth.application.AccessTokenService;
import com.bankflow.auth.application.InvalidCredentialsException;
import com.bankflow.auth.application.LoginService;
import com.bankflow.auth.application.RegistrationService;
import com.bankflow.auth.domain.UserRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final AccessTokenService accessTokenService;
    private final UserRepository users;
    private final UserMapper userMapper;

    AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            AccessTokenService accessTokenService,
            UserRepository users,
            UserMapper userMapper) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.accessTokenService = accessTokenService;
        this.users = users;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userMapper.toResponse(
                registrationService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var user = loginService.authenticate(request.email(), request.password());
        return TokenResponse.from(accessTokenService.issue(user));
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .map(userMapper::toResponse)
                .orElseThrow(InvalidCredentialsException::new);
    }
}
