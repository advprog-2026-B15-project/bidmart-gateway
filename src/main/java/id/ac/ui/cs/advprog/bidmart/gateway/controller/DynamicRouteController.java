package id.ac.ui.cs.advprog.bidmart.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gateway-admin")
public class DynamicRouteController {

    private static final Logger log = LoggerFactory.getLogger(DynamicRouteController.class);

    private final RouteDefinitionWriter routeDefinitionWriter;
    private final ApplicationEventPublisher publisher;

    @Value("${jwt.secret}")
    private String adminSecret;

    public DynamicRouteController(RouteDefinitionWriter routeDefinitionWriter, ApplicationEventPublisher publisher) {
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.publisher = publisher;
    }

    @PostMapping("/routes/auction")
    public Mono<ResponseEntity<String>> updateAuctionRoute(
            ServerHttpRequest request,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "X-Admin-Token", defaultValue = "") String token) {

        String clientIp = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "";
        if (!"127.0.0.1".equals(clientIp) && !"0:0:0:0:0:0:0:1".equals(clientIp)) {
            log.warn("BLOCKED: External IP {} attempted to change routes!", clientIp);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Localhost Only"));
        }

        if (!adminSecret.equals(token)) {
            log.warn("BLOCKED: Unauthorized token attempt from localhost!");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized"));
        }

        String uri = payload.get("uri");
        if (uri == null || uri.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("Error: URI is required"));
        }

        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId("bidmart-auction");
        routeDefinition.setUri(URI.create(uri));

        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("_genkey_0", "/api/auctions/**");
        routeDefinition.setPredicates(List.of(predicate));

        FilterDefinition filter = new FilterDefinition();
        filter.setName("JwtAuthenticationFilter");
        routeDefinition.setFilters(List.of(filter));

        return routeDefinitionWriter.save(Mono.just(routeDefinition))
                .then(Mono.defer(() -> {
                    publisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("Successfully updated bidmart-auction route to {}", uri);
                    return Mono.just(ResponseEntity.ok("Success: Auction route updated to " + uri));
                }))
                .onErrorResume(e -> {
                    log.error("Failed to update route", e);
                    return Mono.just(ResponseEntity.internalServerError().body("Failed: " + e.getMessage()));
                });
    }
}
