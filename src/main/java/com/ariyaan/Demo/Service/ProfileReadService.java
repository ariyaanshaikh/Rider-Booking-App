package com.ariyaan.Demo.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Entity.InvoiceEntity;
import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Entity.RiderDemoEntity;
import com.ariyaan.Demo.Entity.RiderLocationEntity;
import com.ariyaan.Demo.Entity.RiderVerificationDetailsEntity;
import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Entity.UserRole;
import com.ariyaan.Demo.Repository.InvoiceRepository;
import com.ariyaan.Demo.Repository.RideDetailsRepository;
import com.ariyaan.Demo.Repository.RiderDemoRepository;
import com.ariyaan.Demo.Repository.RiderLocationRepository;
import com.ariyaan.Demo.Repository.RiderVerificationDetailsRepository;
import com.ariyaan.Demo.Repository.UserRepository;

/** Read-only data used by authenticated mobile/web profile screens. */
@Service
public class ProfileReadService {

    private static final Collection<String> ACTIVE_RIDE_STATUSES = List.of("ACCEPTED", "STARTED");

    private final UserRepository userRepository;
    private final RiderDemoRepository riderRepository;
    private final RiderVerificationDetailsRepository verificationRepository;
    private final RiderLocationRepository locationRepository;
    private final RideDetailsRepository rideRepository;
    private final InvoiceRepository invoiceRepository;

    public ProfileReadService(UserRepository userRepository, RiderDemoRepository riderRepository,
            RiderVerificationDetailsRepository verificationRepository, RiderLocationRepository locationRepository,
            RideDetailsRepository rideRepository, InvoiceRepository invoiceRepository) {
        this.userRepository = userRepository;
        this.riderRepository = riderRepository;
        this.verificationRepository = verificationRepository;
        this.locationRepository = locationRepository;
        this.rideRepository = rideRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public Map<String, Object> getMyProfile(String username) {
        User user = getUser(username);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", user.getId());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("userName", user.getUserName());
        response.put("email", user.getEmail());
        response.put("mobileNumber", user.getMobileNumber());
        response.put("role", user.getRole());
        response.put("enabled", user.isEnabled());

        if (user.getRole() == UserRole.RIDER) {
            response.put("rider", findRider(user).map(this::riderSummary).orElse(null));
        }
        return response;
    }

    public List<Map<String, Object>> getMyRides(String username) {
        User user = getUser(username);
        List<RideDetailsEntity> rides = user.getRole() == UserRole.RIDER
                ? findRider(user).map(rider -> rideRepository.findByRiderIdOrderByRideIdDesc((int) rider.getId()))
                        .orElse(List.of())
                : rideRepository.findByAdminIdOrderByRideIdDesc((int) user.getId());
        return rides.stream().map(this::rideSummary).toList();
    }

    public Map<String, Object> getMyActiveRide(String username) {
        User user = getUser(username);
        Optional<RideDetailsEntity> activeRide = user.getRole() == UserRole.RIDER
                ? findRider(user).flatMap(rider -> rideRepository
                        .findFirstByRiderIdAndRideStatusInOrderByRideIdDesc((int) rider.getId(), ACTIVE_RIDE_STATUSES))
                : rideRepository.findFirstByAdminIdAndRideStatusInOrderByRideIdDesc((int) user.getId(), ACTIVE_RIDE_STATUSES);
        RideDetailsEntity ride = activeRide.orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("activeRide", ride == null ? null : rideSummary(ride));
        // The booking admin needs the ride-start OTP to display to the passenger.
        // It is never returned to a rider account.
        if (ride != null && user.getRole() == UserRole.ADMIN) {
            response.put("rideOtp", ride.getRideOtp());
        }
        return response;
    }

    public List<InvoiceEntity> getMyInvoices(String username) {
        User user = getUser(username);
        return user.getRole() == UserRole.RIDER
                ? findRider(user).map(rider -> invoiceRepository.findByRiderId((int) rider.getId())).orElse(List.of())
                : invoiceRepository.findByUserId((int) user.getId());
    }

    public Map<String, Object> getMyRiderLocation(String username) {
        RiderDemoEntity rider = getRider(getUser(username));
        RiderLocationEntity location = locationRepository.findById((int) rider.getId()).orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("riderId", rider.getId());
        response.put("location", location == null ? null : Map.of(
                "latitude", location.getLatitude(),
                "longitude", location.getLongitude(),
                "updatedAt", location.getUpdatedAt()));
        return response;
    }

    /** Full KYC/bank form data is visible only to the rider who owns it. */
    public Map<String, Object> getMyVerificationDetails(String username) {
        RiderDemoEntity rider = getRider(getUser(username));
        RiderVerificationDetailsEntity verification = verificationRepository.findById(rider.getId()).orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("riderId", rider.getId());
        response.put("verificationDetails", verification == null ? null : Map.of(
                "aadhaarCardNumber", verification.getAadhaarCardNumber(),
                "panCardNumber", verification.getPanCardNumber(),
                "drivingLicenseNumber", verification.getDrivingLicenseNumber(),
                "bankAccountNumber", verification.getBankAccountNumber(),
                "bankIfscCode", verification.getBankIfscCode(),
                "bankAccountName", verification.getBankAccountName(),
                "verified", verification.getVerified()));
        return response;
    }

    private User getUser(String username) {
        return userRepository.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("Logged-in user was not found"));
    }

    private RiderDemoEntity getRider(User user) {
        if (user.getRole() != UserRole.RIDER) {
            throw new IllegalArgumentException("Only rider accounts have a rider profile");
        }
        return findRider(user)
                .orElseThrow(() -> new IllegalArgumentException("No rider profile exists for this account"));
    }

    private Optional<RiderDemoEntity> findRider(User user) {
        return riderRepository.findByUserId(user.getId());
    }

    private Map<String, Object> riderSummary(RiderDemoEntity rider) {
        RiderVerificationDetailsEntity verification = verificationRepository.findById(rider.getId()).orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("riderId", rider.getId());
        response.put("name", rider.getName());
        response.put("mobileNumber", rider.getMobile_number());
        response.put("status", rider.getStatus());
        response.put("verificationSubmitted", verification != null);
        response.put("verified", verification != null && Boolean.TRUE.equals(verification.getVerified()));
        return response;
    }

    private Map<String, Object> rideSummary(RideDetailsEntity ride) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rideId", ride.getRideId());
        response.put("pickupLatitude", ride.getPickUpLatitude());
        response.put("pickupLongitude", ride.getPickUpLongitude());
        response.put("dropLatitude", ride.getDropLatitude());
        response.put("dropLongitude", ride.getDropLongitude());
        response.put("fare", ride.getFare());
		response.put("chassisNo", ride.getChassisNo());
		response.put("vehicleName", ride.getVehicleName());
        response.put("riderId", ride.getRiderId());
        response.put("adminId", ride.getAdminId());
        response.put("rideStatus", ride.getRideStatus());
        return response;
    }
}
