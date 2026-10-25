package com.smartparking.poc.user;

import com.smartparking.poc.location.ParkingLocation;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Manages parking locations, their spots and pricing. */
@Entity
@DiscriminatorValue("ADMIN")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends User {

    @OneToMany
    @JoinColumn(name = "admin_id")
    private List<ParkingLocation> managedLocations = new ArrayList<>();

    public Admin(String name, String email) {
        super(name, email);
    }
}
