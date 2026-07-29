package com.ariyaan.Demo.Dto;

import jakarta.validation.constraints.NotBlank;

public class RiderVerificationDetailsDTO {

    @NotBlank private String aadhaarCardNumber;
    @NotBlank private String panCardNumber;
    @NotBlank private String drivingLicenseNumber;
    @NotBlank private String bankAccountNumber;
    @NotBlank private String bankIfscCode;
    @NotBlank private String bankAccountName;

    public String getAadhaarCardNumber() { return aadhaarCardNumber; }
    public void setAadhaarCardNumber(String value) { this.aadhaarCardNumber = value; }
    public String getPanCardNumber() { return panCardNumber; }
    public void setPanCardNumber(String value) { this.panCardNumber = value; }
    public String getDrivingLicenseNumber() { return drivingLicenseNumber; }
    public void setDrivingLicenseNumber(String value) { this.drivingLicenseNumber = value; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String value) { this.bankAccountNumber = value; }
    public String getBankIfscCode() { return bankIfscCode; }
    public void setBankIfscCode(String value) { this.bankIfscCode = value; }
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String value) { this.bankAccountName = value; }
}
