package com.smartparking.poc.spot;

import com.smartparking.poc.location.ParkingLocation;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <b>State pattern: Context.</b> A spot delegates every lifecycle action to its current
 * {@link SpotState}. Only {@link #status} is stored; the state object is rebuilt from it on load.
 *
 * <p>{@link #version} gives optimistic locking: if two transactions change the same spot, the
 * second commit fails, so two drivers can never both reserve it.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private ParkingLocation location;

    private String code;

    private int level;

    private String zone;

    @Enumerated(EnumType.STRING)
    private SpotType type;

    private boolean evCharging;

    private boolean accessible;

    @Enumerated(EnumType.STRING)
    private SpotStatus status = SpotStatus.AVAILABLE;

    @Transient
    private SpotState state = new AvailableState();

    @Version
    private long version;

    public ParkingSpot(String code, int level, String zone, SpotType type, boolean evCharging, boolean accessible) {
        this.code = code;
        this.level = level;
        this.zone = zone;
        this.type = type;
        this.evCharging = evCharging;
        this.accessible = accessible;
    }

    public void reserve() {
        state.reserve(this);
    }

    public void occupy() {
        state.occupy(this);
    }

    public void release() {
        state.release(this);
    }

    public void markOutOfService() {
        state.markOutOfService(this);
    }

    public void returnToService() {
        state.returnToService(this);
    }

    /** Called only by the state objects; keeps the stored status in step with the state. */
    void changeState(SpotState newState) {
        this.state = newState;
        this.status = newState.getStatus();
    }

    /** Set by {@link ParkingLocation#addSpot}, which owns the spot (composition). */
    public void assignTo(ParkingLocation location) {
        this.location = location;
    }

    @PostLoad
    void restoreState() {
        this.state = switch (status) {
            case AVAILABLE -> new AvailableState();
            case RESERVED -> new ReservedState();
            case OCCUPIED -> new OccupiedState();
            case OUT_OF_SERVICE -> new OutOfServiceState();
        };
    }
}
