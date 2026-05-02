package io.twba.tk.security;

import java.util.List;

public interface ServiceAuthenticator {

    boolean isAllowed(String serviceName);
    List<String> roles(String serviceName);
}
