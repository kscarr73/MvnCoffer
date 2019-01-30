package com.progbits.mvn.coffer;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author scarr
 */
public class LoginUser {
    private String _userName = null;
    private String _password = null;
    private Map<String, Integer> _roles = new HashMap<>();

    public LoginUser(String userName, String password, String roles) {
        _userName = userName;
        _password = password;
        
        String[] splRole = roles.split(",");
        
        for (String role : splRole) {
            _roles.put(role, 0);
        }
    }

    public String getUserName() {
        return _userName;
    }

    public void setUserName(String userName) {
        this._userName = userName;
    }

    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        this._password = password;
    }

    public Map<String, Integer> getRoles() {
        return _roles;
    }

    public void setRoles(Map<String, Integer> roles) {
        this._roles = roles;
    }

    public boolean hasRole(String role) {
        return _roles.containsKey(role);
    }
}
