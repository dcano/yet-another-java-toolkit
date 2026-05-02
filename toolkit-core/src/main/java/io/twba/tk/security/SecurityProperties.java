package io.twba.tk.security;

import lombok.Data;

@Data
public class SecurityProperties {

    private String jwtSecretKey;
    private long jwtTtl;

}
