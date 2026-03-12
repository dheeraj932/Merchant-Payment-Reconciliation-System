package com.mprs.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mprs.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        // Make sure we fully own the response
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = ErrorResponse.of(
                401,
                "Unauthorized",
                "Authentication is required to access this resource",
                request.getRequestURI()
        );

        String json = objectMapper.writeValueAsString(body);
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}