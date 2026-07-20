package com.ecoexpress.order.api;

import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.domain.OrderStatus;
import com.ecoexpress.order.dto.OrderDtos.OrderPageResponse;
import com.ecoexpress.order.mapper.OrderMapper;
import com.ecoexpress.order.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin order listing (PRD §6). The customer {@code GET /orders} is scoped to the caller's own
 * orders; ops needs every order, optionally filtered by status. Detail and status transitions reuse
 * the existing {@code /orders/{id}} endpoints, which already admit staff with the right authority.
 */
@Tag(name = "Admin Orders")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderMapper mapper;

    @Operation(summary = "List all orders (ops), optionally filtered by status")
    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderPageResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        var content = page.getContent().stream().map(mapper::toSummary).toList();
        return ResponseEntity.ok(new OrderPageResponse(
                content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast()));
    }
}
