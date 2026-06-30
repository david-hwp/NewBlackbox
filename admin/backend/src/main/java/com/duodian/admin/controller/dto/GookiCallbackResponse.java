package com.duodian.admin.controller.dto;

public class GookiCallbackResponse {
    private int code;
    private String message;

    public GookiCallbackResponse() {}

    public GookiCallbackResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static GookiCallbackResponse ok() {
        return new GookiCallbackResponse(200, "OK");
    }

    public static GookiCallbackResponse error(int code, String message) {
        return new GookiCallbackResponse(code, message);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
