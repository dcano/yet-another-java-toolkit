package io.twba.tk.security;

import java.util.List;

public class ServiceAuthenticatorPropertyBased implements ServiceAuthenticator {

    private final List<AllowedService> allowedServices;

    public ServiceAuthenticatorPropertyBased(List<AllowedService> allowedServices) {
        this.allowedServices = allowedServices;
    }

    @Override
    public boolean isAllowed(String serviceName) {
        return allowedServices.stream().anyMatch(s -> s.getServiceName().equals(serviceName));
    }

    @Override
    public List<String> roles(String serviceName) {
        return allowedServices.stream().filter(s -> s.getServiceName().equals(serviceName)).flatMap(s -> s.getRoles().stream()).toList();
    }
}
