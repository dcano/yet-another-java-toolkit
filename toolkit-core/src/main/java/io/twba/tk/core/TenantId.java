package io.twba.tk.core;


import java.util.Objects;

public record TenantId(String value) {
    public TenantId {
        if(Objects.isNull(value)) {
            throw new IllegalArgumentException("Tenant Id cannot be null");
        }
    }
    public static TenantId of(String value) {
        return new TenantId(value);
    }
}


