package com.duodian.admin.controller.dto;

public class PackageVerifyResponse {
    private boolean valid;

    public PackageVerifyResponse() {
    }

    public PackageVerifyResponse(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
