package com.ecoexpress.cart.domain;

import com.ecoexpress.common.domain.BaseEntity;
import com.ecoexpress.identity.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A shopping cart (V4).
 *
 * <p>{@code user} is nullable and {@code sessionKey} exists so a visitor can fill a cart
 * before signing in — PRD §10 puts Browse and Cart ahead of login. The two carts are
 * merged on sign-in. A partial unique index guarantees one ACTIVE cart per user, and one
 * per session: two active carts would make items appear and disappear depending on which
 * row a request happened to load.
 */
@Entity
@Table(name = "carts")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Browser token for an anonymous cart. Null once the cart belongs to a user. */
    @Column(name = "session_key")
    private String sessionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CartStatus status = CartStatus.ACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int totalUnits() {
        return items.stream().mapToInt(CartItem::getQty).sum();
    }
}
