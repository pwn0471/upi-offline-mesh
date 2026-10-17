package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

/**
 * Helper service that:
 *   - seeds demo accounts on startup
 *   - simulates "sender phone creates an encrypted packet" flow
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    @Autowired private AccountRepository accounts;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;


    @PostConstruct
    public void seedAccounts() {
        if (accounts.count() == 0) {
            accounts.save(createDemoAccount(
                    "alice@demo", "Alice", "1234", "5000.00"));
            accounts.save(createDemoAccount(
                    "bob@demo", "Bob", "2345", "1000.00"));
            accounts.save(createDemoAccount(
                    "carol@demo", "Carol", "3456", "2500.00"));
            accounts.save(createDemoAccount(
                    "dave@demo", "Dave", "4567", "500.00"));

            log.info("Seeded 4 demo accounts");
        } else {
            for (Account account : accounts.findAll()) {
                if (account.getPinHash() == null
                        || account.getPinHash().isBlank()) {
                    String demoPin = switch (account.getVpa()) {
                        case "alice@demo" -> "1234";
                        case "bob@demo" -> "2345";
                        case "carol@demo" -> "3456";
                        case "dave@demo" -> "4567";
                        default -> null;
                    };

                    if (demoPin == null) {
                        log.warn("No demo PIN assigned to account {}",
                                account.getVpa());
                        continue;
                    }

                    account.setPinHash(passwordEncoder.encode(demoPin));
                    accounts.save(account);
                }
            }
            log.info("Initialized missing demo account PIN hashes");
        }
    }

    private Account createDemoAccount(
            String vpa, String holderName, String pin, String balance) {
        Account account = new Account(
                vpa, holderName, new BigDecimal(balance));
        account.setPinHash(passwordEncoder.encode(pin));
        return account;
    }


    /**
     * Simulates the sender's phone:
     *   1. Build a PaymentInstruction with a fresh nonce + signedAt timestamp.
     *   2. Encrypt with the server's public key (hybrid RSA+AES).
     *   3. Wrap in a MeshPacket with TTL.
     *
     * In a real Android app, this exact code (minus the server-side reference)
     * would run on the phone. The phone would have already cached the server's
     * public key during a previous online session.
     */
    public MeshPacket createPacket(String senderVpa, String receiverVpa,
                                   BigDecimal amount, String pin, int ttl) throws Exception {
        PaymentInstruction instruction = new PaymentInstruction(
                senderVpa,
                receiverVpa,
                amount,
                null,
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli()
        );
        instruction.setPin(pin);

        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext(ciphertext);
        return packet;
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
