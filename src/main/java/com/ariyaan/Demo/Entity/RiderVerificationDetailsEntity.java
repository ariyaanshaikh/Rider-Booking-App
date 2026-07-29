package com.ariyaan.Demo.Entity;

import jakarta.persistence.*;

@Entity
@Table(name = "RIDER_VERIFICATION_DETAILS")
public class RiderVerificationDetailsEntity {
    @Id
    @Column(name = "RIDER_ID")
    private Long riderId;
    @Column(name = "AADHAAR_CARD_NUMBER", nullable = false) private String aadhaarCardNumber;
    @Column(name = "PAN_CARD_NUMBER", nullable = false) private String panCardNumber;
    @Column(name = "DRIVING_LICENSE_NUMBER", nullable = false) private String drivingLicenseNumber;
    @Column(name = "BANK_ACCOUNT_NUMBER", nullable = false) private String bankAccountNumber;
    @Column(name = "BANK_IFSC_CODE", nullable = false) private String bankIfscCode;
    @Column(name = "BANK_ACCOUNT_NAME", nullable = false) private String bankAccountName;
    @Column(name = "IS_VERIFIED", nullable = false) private Boolean verified = false;

    public Long getRiderId() { return riderId; }
    public void setRiderId(Long value) { this.riderId = value; }
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
    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean value) { this.verified = value; }
}
