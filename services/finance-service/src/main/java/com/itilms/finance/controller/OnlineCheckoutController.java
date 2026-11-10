package com.itilms.finance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.finance.dto.request.OnlineCheckoutDtos.CheckoutConfig;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.ConfirmRequest;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.CreateOrderRequest;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.OrderCreated;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.OrderStatus;
import com.itilms.finance.online.OnlineCheckoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments/online")
@Tag(name = "Online payments", description = "Buying a course with Razorpay")
@RequiredArgsConstructor
public class OnlineCheckoutController {

    private final OnlineCheckoutService service;

    @Operation(summary = "Is online payment set up?")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/config")
    public CheckoutConfig config() {
        return service.config();
    }

    @Operation(summary = "Start buying a course", description = "Students only. The amount is the course fee, fixed on the server.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/orders")
    public ResponseEntity<OrderCreated> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrder(request));
    }

    @Operation(summary = "The payment finished", description = "Checks Razorpay's signature, records the fee and receipt, "
            + "and places the student in a batch.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/verify")
    public OrderStatus verify(@Valid @RequestBody ConfirmRequest request) {
        return service.confirm(request);
    }

    @Operation(summary = "Place a paid student in a batch", description = "Retry for an order that is PAID but not yet ENROLLED.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/orders/{orderId}/finish")
    public OrderStatus finish(@PathVariable String orderId) {
        return service.finish(orderId);
    }

    @Operation(summary = "One order", description = "The student who placed it, or staff.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/orders/{orderId}")
    public OrderStatus get(@PathVariable String orderId) {
        return service.get(orderId);
    }

    @Operation(summary = "Razorpay webhook", description = "Public; the body is checked against the webhook secret.")
    @SecurityRequirements
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        service.webhook(body, signature);
        return ResponseEntity.ok().build();
    }
}
