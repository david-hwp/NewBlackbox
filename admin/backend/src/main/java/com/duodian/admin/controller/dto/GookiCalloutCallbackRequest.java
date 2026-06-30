package com.duodian.admin.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

public class GookiCalloutCallbackRequest {
    @JsonAlias({"id", "cdr_id", "cdrId"})
    private String cdrId;

    @JsonAlias({"taskId", "task_id"})
    private String taskId;

    private String phone;

    @JsonAlias({"realPhone", "real_phone"})
    private String realPhone;

    @JsonAlias({"callTime", "call_time"})
    private Object callTime;

    @JsonAlias({"callState", "call_state"})
    private Object callState;

    @JsonAlias({"callStateText", "call_state_text", "stateText", "state_text"})
    private String callStateText;

    private Object duration;
    private Object minute;
    private Object money;
    private String grade;
    private String remark;
    private Object params;
    private Object columns;
    private Object content;
    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        extra.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }

    public String getCdrId() { return cdrId; }
    public void setCdrId(String cdrId) { this.cdrId = cdrId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRealPhone() { return realPhone; }
    public void setRealPhone(String realPhone) { this.realPhone = realPhone; }
    public Object getCallTime() { return callTime; }
    public void setCallTime(Object callTime) { this.callTime = callTime; }
    public Object getCallState() { return callState; }
    public void setCallState(Object callState) { this.callState = callState; }
    public String getCallStateText() { return callStateText; }
    public void setCallStateText(String callStateText) { this.callStateText = callStateText; }
    public Object getDuration() { return duration; }
    public void setDuration(Object duration) { this.duration = duration; }
    public Object getMinute() { return minute; }
    public void setMinute(Object minute) { this.minute = minute; }
    public Object getMoney() { return money; }
    public void setMoney(Object money) { this.money = money; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Object getParams() { return params; }
    public void setParams(Object params) { this.params = params; }
    public Object getColumns() { return columns; }
    public void setColumns(Object columns) { this.columns = columns; }
    public Object getContent() { return content; }
    public void setContent(Object content) { this.content = content; }
}
