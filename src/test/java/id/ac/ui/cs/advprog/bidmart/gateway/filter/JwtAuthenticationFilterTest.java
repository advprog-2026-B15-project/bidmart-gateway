package id.ac.ui.cs.advprog.bidmart.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private final String secret = "IniRahasiaBidMartYangSangatPanjangSekali32Karakter";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", secret);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void testBypassPublicEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        result.block();
        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void testBypassPublicGetListingsAndAuctions() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/listings/some-id").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        result.block();
        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void testSecuredEndpointMissingAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/listings").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        result.block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void testSecuredEndpointInvalidBearerFormat() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, "InvalidFormat token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        result.block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void testSecuredEndpointValidToken() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-123")
                .claim("role", "SELLER")
                .signWith(key)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        GatewayFilter gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        result.block();

        ServerWebExchange mutatedExchange = captor.getValue();
        assertNotNull(mutatedExchange);
        assertEquals("user-123", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("SELLER", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void testSecuredEndpointExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-123")
                .claim("role", "SELLER")
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        result.block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }
}
