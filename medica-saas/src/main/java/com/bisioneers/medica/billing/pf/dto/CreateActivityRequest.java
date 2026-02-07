package com.bisioneers.medica.billing.pf.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateActivityRequest {
    private String description;
    private double amount;
    private double discount;

    @JsonProperty("idPaymentStation")
    private int idPaymentStation;

    public CreateActivityRequest() {}

    public CreateActivityRequest(String description, double amount, double discount, int idPaymentStation) {
        this.description = description;
        this.amount = amount;
        this.discount = discount;
        this.idPaymentStation = idPaymentStation;
    }

    public String getDescription() { return description; }
    public double getAmount() { return amount; }
    public double getDiscount() { return discount; }
    public int getIdPaymentStation() { return idPaymentStation; }

    public void setDescription(String description) { this.description = description; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setDiscount(double discount) { this.discount = discount; }
    public void setIdPaymentStation(int idPaymentStation) { this.idPaymentStation = idPaymentStation; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class CreateActivityResponse {
    private boolean success;
    private Object data; // PF no documenta el shape completo; lo guardamos genérico

    public boolean isSuccess() { return success; }
    public Object getData() { return data; }

    public void setSuccess(boolean success) { this.success = success; }
    public void setData(Object data) { this.data = data; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ActivitiesQueryResponse {
    private boolean success;
    private Object data;

    public boolean isSuccess() { return success; }
    public Object getData() { return data; }

    public void setSuccess(boolean success) { this.success = success; }
    public void setData(Object data) { this.data = data; }
}

