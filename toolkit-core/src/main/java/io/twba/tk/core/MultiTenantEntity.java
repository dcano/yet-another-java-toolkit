package io.twba.tk.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public abstract class MultiTenantEntity extends Entity {

    @NotNull(message = "lblTenantIdNotNull")
    @Valid
    private final TenantId tenantId;

    public MultiTenantEntity(TenantId tenantId, Long version) {
        super(version);
        this.tenantId = tenantId;
        this.validateProperty("tenantId");
    }

}
