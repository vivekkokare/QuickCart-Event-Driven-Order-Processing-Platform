package com.blinkitclone.orderservice.api.rest;

import com.blinkitclone.orderservice.api.dto.PlaceOrderRequest;
import com.blinkitclone.orderservice.api.dto.PlaceOrderResponse;
import com.blinkitclone.orderservice.api.mapper.OrderApiMapper;
import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only class in this codebase that knows it is being driven over HTTP.
 * It depends on PlaceOrderUseCase (an interface), not PlaceOrderService (the
 * concrete class) — Spring injects the implementation at runtime. The
 * controller's job is strictly: deserialize, validate, map to a command,
 * delegate, map the result back. No business logic belongs here.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final OrderApiMapper mapper;

    public OrderController(PlaceOrderUseCase placeOrderUseCase, OrderApiMapper mapper) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        var result = placeOrderUseCase.placeOrder(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
    }
}
