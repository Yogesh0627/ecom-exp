package com.ecoexpress.order.api;

import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.identity.repository.UserRepository;
import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.dto.OrderDtos.CancelRequest;
import com.ecoexpress.order.dto.OrderDtos.CheckoutRequest;
import com.ecoexpress.order.dto.OrderDtos.OrderPageResponse;
import com.ecoexpress.order.dto.OrderDtos.OrderResponse;
import com.ecoexpress.order.dto.OrderDtos.OrderSummaryResponse;
import com.ecoexpress.order.dto.OrderDtos.TransitionRequest;
import com.ecoexpress.order.domain.OrderStatus;
import com.ecoexpress.order.mapper.OrderMapper;
import com.ecoexpress.order.domain.Invoice;
import com.ecoexpress.order.repository.OrderRepository;
import com.ecoexpress.order.repository.OrderStatusHistoryRepository;
import com.ecoexpress.order.service.InvoiceService;
import com.ecoexpress.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final OrderMapper mapper;

    @Operation(summary = "Place an order from the current cart")
    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<OrderResponse> checkout(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CheckoutRequest request) {
        Order order = orderService.checkout(user.getId(), request.addressId(),
                request.shippingFee(), request.customerNote(), request.couponCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toOrder(order, historyRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())));
    }

    /** Status buckets for the My Orders tabs. ALL (or unknown) returns everything. */
    private static final java.util.Set<OrderStatus> ACTIVE_STATUSES = java.util.EnumSet.of(
            OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.CONFIRMED,
            OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY);
    private static final java.util.Set<OrderStatus> CANCELLED_STATUSES = java.util.EnumSet.of(
            OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.REFUNDED);

    @Operation(summary = "List my orders, optionally by bucket (active/delivered/cancelled/all)")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<OrderPageResponse> myOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "all") String bucket,
            @PageableDefault(size = 10) Pageable pageable) {
        java.util.UUID uid = user.getId();
        Page<Order> page = switch (bucket == null ? "all" : bucket.toLowerCase()) {
            case "active", "in_progress" ->
                    orderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(uid, ACTIVE_STATUSES, pageable);
            case "delivered" ->
                    orderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
                            uid, java.util.Set.of(OrderStatus.DELIVERED), pageable);
            case "cancelled" ->
                    orderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(uid, CANCELLED_STATUSES, pageable);
            default -> orderRepository.findByUserIdOrderByCreatedAtDesc(uid, pageable);
        };
        var content = page.getContent().stream().map(mapper::toSummary).toList();
        return ResponseEntity.ok(new OrderPageResponse(
                content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast()));
    }

    /**
     * Ownership is checked here, not by the query alone. Returning 404 rather than 403
     * for someone else's order avoids confirming that the id exists.
     */
    @Operation(summary = "Get one of my orders")
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new NotFoundException("No order " + id));

        boolean owner = order.getUser().getId().equals(user.getId());
        boolean staff = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("order:read"));
        if (!owner && !staff) {
            throw new NotFoundException("No order " + id);
        }
        return ResponseEntity.ok(
                mapper.toOrder(order, historyRepository.findByOrderIdOrderByCreatedAtAsc(id)));
    }

    /**
     * The tax invoice PDF for an order, generated on demand from the frozen order data. Owner or
     * staff only; available once the order is paid. Streamed (not a public link) because it carries
     * the customer's name and address.
     */
    @Operation(summary = "Download an order's tax invoice (PDF)")
    @GetMapping("/{id}/invoice")
    @Transactional
    public ResponseEntity<byte[]> invoice(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new NotFoundException("No order " + id));
        boolean owner = order.getUser().getId().equals(user.getId());
        boolean staff = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("order:read"));
        if (!owner && !staff) {
            throw new NotFoundException("No order " + id);
        }
        Invoice invoice = invoiceService.getOrCreate(order);
        byte[] pdf = invoiceService.renderPdf(order, invoice);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"")
                .body(pdf);
    }

    @Operation(summary = "Cancel one of my orders (before it ships)")
    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody CancelRequest request) {
        Order order = orderService.cancelByCustomer(id, user.getId(), request.reason());
        return ResponseEntity.ok(
                mapper.toOrder(order, historyRepository.findByOrderIdOrderByCreatedAtAsc(id)));
    }

    @Operation(summary = "Advance an order's status (ops)")
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('order:write')")
    @Transactional
    public ResponseEntity<OrderResponse> transition(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @Valid @RequestBody TransitionRequest request) {
        var user = userRepository.findById(actor.getId()).orElse(null);
        Order order = orderService.transition(id, request.status(), user, request.note());
        return ResponseEntity.ok(
                mapper.toOrder(order, historyRepository.findByOrderIdOrderByCreatedAtAsc(id)));
    }
}
