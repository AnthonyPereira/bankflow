package com.bankflow.auth.api.auth;

import com.bankflow.auth.api.auth.dto.LoginRequest;
import com.bankflow.auth.api.auth.dto.RegisterRequest;
import com.bankflow.auth.api.auth.dto.TokenResponse;
import com.bankflow.auth.api.auth.dto.UserResponse;
import com.bankflow.auth.application.login.LoginService;
import com.bankflow.auth.application.registration.RegistrationService;
import com.bankflow.auth.application.token.AccessTokenService;
import com.bankflow.auth.application.token.InvalidRefreshTokenException;
import com.bankflow.auth.application.token.RefreshTokenService;
import com.bankflow.auth.application.user.CurrentUserService;
import com.bankflow.auth.domain.user.User;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookies refreshCookies;
    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;

    AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService,
            RefreshCookies refreshCookies,
            CurrentUserService currentUserService,
            UserMapper userMapper) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookies = refreshCookies;
        this.currentUserService = currentUserService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userMapper.toResponse(
                registrationService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var user = loginService.authenticate(request.email(), request.password());
        return tokens(user, refreshTokenService.issue(user.getId()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        var rotation = refreshTokenService.rotate(refreshToken);
        return tokens(rotation.user(), rotation.refreshToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear())
                .build();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userMapper.toResponse(
                currentUserService.getCurrentUser(UUID.fromString(jwt.getSubject())));
    }

    private ResponseEntity<TokenResponse> tokens(
            User user, RefreshTokenService.IssuedRefreshToken refreshToken) {
        var cookie = refreshCookies.create(refreshToken.value(), refreshToken.timeToLive());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(TokenResponse.from(accessTokenService.issue(user)));
    }
}
