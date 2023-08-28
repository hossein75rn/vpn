
package com.bandarvpn.ang.data.model;

import java.util.List;

public class VpnConfigs {
    private String status;
    private String message;
    private int days;
    private String configs;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public String getConfigs() {
        return configs;
    }

    public void setConfigs(String configs) {
        this.configs = configs;
    }
}

