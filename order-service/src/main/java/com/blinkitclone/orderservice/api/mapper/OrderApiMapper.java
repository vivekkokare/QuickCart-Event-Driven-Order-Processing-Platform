package com.blinkitclone.orderservice.api.mapper;

import com.blinkitclone.orderservice.api.dto.PlaceOrderRequest;
import com.blinkitclone.orderservice.api.dto.PlaceOrderResponse;
import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import org.springframework.stereotype.Component;

/**
 * Translates between the REST wire format and the application layer's
 * command/result vocabulary. Kept as an explicit, hand-written mapper rather
 * than a code-generation library (e.g. MapStruct) at this stage — the mapping
 * is simple enough that explicitness aids learning; we can introduce MapStruct
 * later once the mapping surface grows.
 */
@Component
public class OrderApiMapper {

    public PlaceOrderCommand toCommand(PlaceOrderRequest request) {
        var items = request.items().stream()
                .map(item -> new OrderItemCommand(item.productId(), item.productName(), item.quantity(), item.unitPrice()))
                .toList();
        return new PlaceOrderCommand(request.customerId(), items);
    }

    public PlaceOrderResponse toResponse(PlaceOrderResult result) {
        return new PlaceOrderResponse(result.orderId(), result.status(), result.totalAmount(), result.currency());
    }
}
