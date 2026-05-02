package io.twba.tk.core;

interface SecurityAware {

    void setUserInfo(DomainUser domainUser);
    DomainUser extractUserInfo();
    void setSecurityToken(String securityToken);
    String getSecurityToken();

}
