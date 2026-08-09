package com.ariyaan.Demo.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request payload used to create a rider available for matching. */
public class RiderDetailsDTO {

    @NotBlank(message = "Rider name is required")
    private String name;

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @Pattern(regexp = "AVAILABLE|OFFLINE", message = "Status must be AVAILABLE or OFFLINE")
    private String status = "AVAILABLE";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
