package com.bandarvpn.ang.data.model;

public class Login {

    public final String statusFailed = "failed";
    public final String statusSuccess = "success";
    private String status;
    private String message;
    private String uuid;

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

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}