package com.ecoexpress.order.domain;

/** Mirrors addresses_type_chk in V4. */
public enum AddressType {
    HOME,
    WORK,
    /** No owning user — see addresses_owner_chk. */
    WAREHOUSE,
    OTHER
}
