package com.hasancetin.platform.auth.api;

import com.hasancetin.platform.auth.api.AuthDtos.LoginRequest;
import com.hasancetin.platform.auth.api.AuthDtos.RegisterRequest;
import com.hasancetin.platform.auth.api.AuthDtos.RegisterResponse;
import com.hasancetin.platform.auth.api.AuthDtos.TokenResponse;
import com.hasancetin.platform.auth.application.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }
}
