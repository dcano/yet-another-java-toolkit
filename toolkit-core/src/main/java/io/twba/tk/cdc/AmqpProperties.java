package io.twba.tk.cdc;

import lombok.Data;

@Data
public class AmqpProperties {

    public String host;
    private int port;
    private String username;
    private String password;

}
