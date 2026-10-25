package com.smartparking.poc.notification;

import com.smartparking.poc.user.Driver;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A message sent to a driver, kept so it can be listed later. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private Driver recipient;

    @Enumerated(EnumType.STRING)
    private ChannelType channelType;

    private String message;

    private Instant sentAt;

    public Notification(Driver recipient, ChannelType channelType, String message, Instant sentAt) {
        this.recipient = recipient;
        this.channelType = channelType;
        this.message = message;
        this.sentAt = sentAt;
    }
}
