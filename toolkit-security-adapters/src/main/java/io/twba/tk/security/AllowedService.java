package io.twba.tk.security;

import lombok.Data;

import java.util.List;

@Data
public class AllowedService {

    private String serviceName;
    private List<String> roles;

}
