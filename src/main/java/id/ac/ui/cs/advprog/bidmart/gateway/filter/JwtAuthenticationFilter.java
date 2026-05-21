package id.ac.ui.cs.advprog.bidmart.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final List<String> openApiEndpoints = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/verify-email",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/logout",
            "/api/auth/2fa/verify",
            "/api/categories"
    );

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (ServerWebExchange exchange, GatewayFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String method = request.getMethod().name();

            if (!isSecured(path, method)) {
                log.info("[JWT] BYPASS: {} {} -> endpoint publik, tidak perlu autentikasi.", method, path);
                return chain.filter(exchange);
            }

            log.debug("[JWT] SECURED: {} {} -> memerlukan autentikasi JWT.", method, path);

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                log.warn("[JWT] DITOLAK: {} {} -> tidak ada header 'Authorization'.", method, path);
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).getFirst();
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[JWT] DITOLAK: {} {} -> header Authorization ada, tapi formatnya bukan 'Bearer <token>'. Nilai: '{}'",
                        method, path, authHeader);
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                log.info("[JWT] DITERIMA: {} {} -> userId='{}', role='{}'.", method, path, userId, role);

                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role != null ? role : "")
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (ExpiredJwtException e) {
                log.warn("[JWT] DITOLAK: {} {} -> token sudah kadaluarsa (expired). Detail: {}", method, path, e.getMessage());
                return onError(exchange, HttpStatus.UNAUTHORIZED);

            } catch (JwtException e) {
                log.warn("[JWT] DITOLAK: {} {} -> token tidak valid / signature salah. Detail: {}", method, path, e.getMessage());
                return onError(exchange, HttpStatus.UNAUTHORIZED);

            } catch (Exception e) {
                log.error("[JWT] ERROR: {} {} -> terjadi error tak terduga saat memproses token. Detail: {}", method, path, e.getMessage());
                return onError(exchange, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private boolean isSecured(String path, String method) {
        if ("GET".equals(method)
                && (path.startsWith("/api/listings") || path.startsWith("/api/auctions"))) {
            return false;
        }

        return openApiEndpoints.stream().noneMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    public static class Config {
    }
}