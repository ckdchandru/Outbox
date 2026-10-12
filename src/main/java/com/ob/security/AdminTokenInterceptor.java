package com.ob.security;

import com.ob.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Token";

    private final byte[] expectedToken;

    public AdminTokenInterceptor(@Value("${outbox.admin.token}") String adminToken) {
        this.expectedToken = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(HEADER);
        if (token == null) {
            throw new UnauthorizedException("missing " + HEADER + " header");
        }

        // constant time compare
        if (!MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("invalid admin token");
        }
        return true;
    }
}
