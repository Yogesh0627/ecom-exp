package com.ecoexpress.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seller identity printed on the tax invoice. Sourced from config so it is set once per
 * environment; the GSTIN and FSSAI number are legally required on an Indian tax invoice.
 */
@ConfigurationProperties(prefix = "ecoexpress.invoice.seller")
public class InvoiceProperties {

    private String name = "EcoExpress Organics";
    private String gstin = "";
    private String fssai = "";
    private String addressLine = "";
    private String city = "";
    private String state = "";
    private String pincode = "";
    private String email = "";
    private String phone = "";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getFssai() { return fssai; }
    public void setFssai(String fssai) { this.fssai = fssai; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
