package com.duynh.shopee.auth;

import com.duynh.shopee.user.User;
import com.duynh.shopee.user.UserRepository;
import com.duynh.shopee.user.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public AuthResponse register(String email, String rawPassword) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
        String hashedPassword = passwordEncoder.encode(rawPassword);
        User user = userRepository.save(new User(email, hashedPassword, "USER"));
        return buildAuthResponse(user);
    }

    public AuthResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, jwtService.getExpirationSeconds(), UserResponse.from(user));
    }
}
