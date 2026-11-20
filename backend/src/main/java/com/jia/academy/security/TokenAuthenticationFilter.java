package com.jia.academy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    public TokenAuthenticationFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String rawToken = header.substring(7).trim();
            if (!rawToken.isBlank() && rawToken.length() <= 160) {
                String hash = sha256(rawToken);
                List<CurrentUser> users = jdbc.query("""
                    SELECT u.id, u.name, u.email,
                           COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::varchar[]) AS roles,
                           COALESCE(array_agg(DISTINCT p.code) FILTER (WHERE p.code IS NOT NULL), ARRAY[]::varchar[]) AS permissions,
                           COALESCE(array_agg(DISTINCT am.academy_id) FILTER (WHERE am.academy_id IS NOT NULL), ARRAY[]::uuid[]) AS academy_ids
                    FROM api_tokens t
                    JOIN users u ON u.id = t.user_id
                    JOIN academy_memberships am ON am.user_id = u.id AND am.status = 'ACTIVE'
                    JOIN academies a ON a.id = am.academy_id AND a.status = 'ACTIVE'
                    LEFT JOIN user_roles ur ON ur.user_id = u.id
                    LEFT JOIN roles r ON r.id = ur.role_id
                    LEFT JOIN role_permissions rp ON rp.role_id = r.id
                    LEFT JOIN permissions p ON p.id = rp.permission_id
                    WHERE t.token_hash = ? AND t.revoked_at IS NULL AND t.expires_at > ?
                      AND u.status = 'ACTIVE'
                    GROUP BY u.id, u.name, u.email
                    """, (rs, rowNum) -> new CurrentUser(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
                        List.of((String[]) rs.getArray("roles").getArray()),
                        List.of((String[]) rs.getArray("permissions").getArray()),
                        List.of((UUID[]) rs.getArray("academy_ids").getArray())
                    ), hash, Timestamp.from(Instant.now()));

                if (!users.isEmpty()) {
                    CurrentUser user = users.getFirst();
                    var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
                    user.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                    user.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    jdbc.update("UPDATE api_tokens SET last_used_at = ? WHERE token_hash = ?", Timestamp.from(Instant.now()), hash);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
