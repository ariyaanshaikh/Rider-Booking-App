package com.ariyaan.Demo.Service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ariyaan.Demo.Dto.RiderVerificationDetailsDTO;
import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Entity.RiderVerificationDetailsEntity;
import com.ariyaan.Demo.Repository.RideDetailsRepository;
import com.ariyaan.Demo.Repository.RiderDemoRepository;
import com.ariyaan.Demo.Repository.RiderVerificationDetailsRepository;

@Service
public class RiderProfileService {
    private final RiderVerificationDetailsRepository detailsRepository;
    private final RiderDemoRepository riderRepository;
    private final RideDetailsRepository rideRepository;

    public RiderProfileService(RiderVerificationDetailsRepository detailsRepository,
            RiderDemoRepository riderRepository, RideDetailsRepository rideRepository) {
        this.detailsRepository = detailsRepository;
        this.riderRepository = riderRepository;
        this.rideRepository = rideRepository;
    }

    @Transactional
    public RiderVerificationDetailsEntity saveDetails(long riderId, RiderVerificationDetailsDTO dto) {
        if (!riderRepository.existsById(riderId)) throw new IllegalArgumentException("Rider not found: " + riderId);
        RiderVerificationDetailsEntity entity = detailsRepository.findById(riderId)
                .orElseGet(RiderVerificationDetailsEntity::new);
        entity.setRiderId(riderId);
        entity.setAadhaarCardNumber(dto.getAadhaarCardNumber());
        entity.setPanCardNumber(dto.getPanCardNumber());
        entity.setDrivingLicenseNumber(dto.getDrivingLicenseNumber());
        entity.setBankAccountNumber(dto.getBankAccountNumber());
        entity.setBankIfscCode(dto.getBankIfscCode());
        entity.setBankAccountName(dto.getBankAccountName());
        entity.setVerified(false);
        return detailsRepository.save(entity);
    }

    @Transactional
    public RiderVerificationDetailsEntity setVerified(long riderId, boolean verified) {
        RiderVerificationDetailsEntity entity = detailsRepository.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider verification details not found: " + riderId));
        entity.setVerified(verified);
        return detailsRepository.save(entity);
    }

    public RiderVerificationDetailsEntity getDetails(long riderId) {
        return detailsRepository.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider verification details not found: " + riderId));
    }

    public List<RideDetailsEntity> getHistory(long riderId) {
        return rideRepository.findByRiderIdOrderByRideIdDesc((int) riderId);
    }
}
