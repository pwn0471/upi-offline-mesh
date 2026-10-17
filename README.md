# UPI Offline Mesh — Demo

A Spring Boot application demonstrating **mesh-routed deferred payment settlement**. An encrypted payment packet travels between simulated offline devices and is uploaded by a bridge device when it has internet access. The backend checks the packet, verifies the sender's PIN, detects duplicate deliveries, and settles valid payments in a MySQL ledger.

This repository contains the Spring Boot backend and a software mesh simulator. It does not use real Bluetooth hardware or connect to the real UPI network.

## Table of Contents

1. [Project overview](#project-overview)
2. [Prerequisites](#prerequisites)
3. [Setup and run](#setup-and-run)
4. [Demo workflow](#demo-workflow)
5. [Architecture](#architecture)
6. [Security and payment processing](#security-and-payment-processing)
7. [Project structure](#project-structure)
8. [API reference](#api-reference)
9. [Tests](#tests)
10. [Limitations and production considerations](#limitations-and-production-considerations)
11. [Troubleshooting](#troubleshooting)

---

## Project overview

The demo illustrates:
- Hybrid encryption using RSA-OAEP and AES-GCM.
- Packet distribution between virtual devices in a software mesh.
- Bridge upload to the backend.
- Rejection of tampered packets and invalid payment instructions.
- BCrypt-hashed demo account PINs checked before settlement.
- Duplicate packet detection and transactional balance updates in MySQL.

These are demonstration features, not a production banking implementation.

## Prerequisites

- JDK 17 or newer.
- MySQL 8.0 or newer.
- Git, if cloning the repository.

The project includes the Maven Wrapper, so Maven does not need to be installed separately.

## Setup and run

### 1. Clone the repository (if needed)

```powershell
git clone https://github.com/pwn0471/upi-offline-mesh.git
cd upi-offline-mesh
```

If the project is already on your computer, use its existing directory.

### 2. Create the MySQL database

Start MySQL and execute:

```sql
CREATE DATABASE upi_offline_mesh;
```

The connection is configured in `src/main/resources/application.properties`.

### 3. Configure database credentials

Set these environment variables before starting the app:

- `DB_USERNAME` — your MySQL username.
- `DB_PASSWORD` — your MySQL password.

Configure them in your IDE run configuration or in the terminal session that launches the app. Never commit real credentials to Git.

For PowerShell, set them for the current terminal session:

```powershell
$env:DB_USERNAME = "root"
$securePassword = Read-Host "Enter your MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
```

These PowerShell variables apply only to the current terminal session.

### 4. Start on Windows

From the project root, run:

```powershell
.\mvnw.cmd spring-boot:run
```

Alternatively, run `UpiMeshApplication` from IntelliJ IDEA after setting the two environment variables in the run configuration.

### 5. Open the dashboard

After Spring Boot starts successfully, open **http://localhost:8080**.

### 6. Run automated tests

Make sure `DB_USERNAME` and `DB_PASSWORD` are available to the test process, then run:

```powershell
.\mvnw.cmd test
```

### 7. Stop the server

Press `Ctrl+C` in the terminal running the application.

---

## Demo workflow

### Step 1 — Compose a payment

Choose a sender, receiver, amount, and four-digit demo PIN, then use the dashboard's send/inject action. The backend demo helper creates and encrypts the payment instruction and injects a packet into a virtual device. This simulates a sender phone; it is not a real mobile client.

### Step 2 — Run gossip rounds

Use the gossip action to distribute packets between virtual devices. Device-to-device communication is simulated in software; it does not use actual Bluetooth.

### Step 3 — Upload from a bridge

Use the bridge flush/upload action. The simulated bridge sends packets to `/api/bridge/ingest`. The backend processes the packet and settles a valid payment if the sender has sufficient funds.

### Step 4 — Verify the result

Check the dashboard's account balances and transaction ledger. You can also inspect balances in MySQL.

Default demo accounts and PINs:

| Account | Demo PIN |
|---|---|
| `alice@demo` | `1234` |
| `bob@demo` | `2345` |
| `carol@demo` | `3456` |
| `dave@demo` | `4567` |

These PINs are for local demonstration only. Do not reuse them for real accounts.

---

## Architecture

```text
Dashboard / Demo helper
        |
        | creates encrypted payment packet
        v
Virtual device mesh simulator
        |
        | gossip between simulated devices
        v
Bridge upload
        |
        | POST /api/bridge/ingest
        v
BridgeIngestionService
        |
        +--> Hash ciphertext and check duplicate claim
        +--> Decrypt packet and verify authenticated ciphertext
        +--> Check packet freshness
        +--> Verify sender PIN against BCrypt hash
        +--> Validate sender, receiver, amount, and available balance
        v
SettlementService (@Transactional)
        |
        +--> Debit sender
        +--> Credit receiver
        +--> Record transaction in MySQL
```

## Security and payment processing

### Hybrid encryption

The crypto service uses a hybrid pattern:
1. Generate a fresh AES key for a packet.
2. Encrypt the payment instruction with AES-GCM.
3. Encrypt the AES key using RSA-OAEP and the server's public key.
4. The server uses its private key to unwrap the AES key and decrypt the payload.

AES-GCM authenticates the encrypted data; tampering causes decryption/verification to fail.

**Important:** The current demo creates and encrypts packets on the backend through a demo endpoint. It does not implement a real mobile sender encrypting data while fully offline.

### PIN handling

- Demo account PINs are stored as BCrypt hashes in MySQL.
- The demo payment instruction carries the PIN inside the encrypted packet payload.
- The backend checks the PIN against the sender's BCrypt hash before settlement.
- The accounts API excludes the stored PIN hash.

The demo endpoint receives the PIN as part of an HTTP request. Use this only in a local demo. A production system needs HTTPS, user/device authentication, secure client-side handling, rate limiting, and a banking-grade PIN verification design.

### Duplicate detection and settlement

The current idempotency service uses an in-memory `ConcurrentHashMap` to claim packet ciphertext hashes. This helps the single-JVM demo reject duplicate deliveries. The transaction table also has a unique packet-hash constraint as a further safeguard.

The in-memory cache is not shared between application instances and is cleared when the application restarts. Production would require a persistent/distributed idempotency strategy and recovery handling.

### Validation

The settlement logic checks that:
- Sender and receiver are different accounts.
- The amount is positive.
- Both accounts exist.
- The sender has sufficient funds.
- The sender PIN is valid before settlement.

The debit, credit, and ledger write are performed transactionally.

---

## Project structure

```text
upi-offline-mesh/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── README.md
└── src/
    ├── main/
    │   ├── java/com/demo/upimesh/
    │   │   ├── UpiMeshApplication.java
    │   │   ├── controller/
    │   │   │   ├── ApiController.java
    │   │   │   └── DashboardController.java
    │   │   ├── crypto/
    │   │   │   ├── ServerKeyHolder.java
    │   │   │   └── HybridCryptoService.java
    │   │   ├── model/
    │   │   │   ├── Account.java
    │   │   │   ├── AccountRepository.java
    │   │   │   ├── Transaction.java
    │   │   │   ├── TransactionRepository.java
    │   │   │   ├── MeshPacket.java
    │   │   │   └── PaymentInstruction.java
    │   │   ├── service/
    │   │   │   ├── DemoService.java
    │   │   │   ├── VirtualDevice.java
    │   │   │   ├── MeshSimulatorService.java
    │   │   │   ├── IdempotencyService.java
    │   │   │   ├── SettlementService.java
    │   │   │   └── BridgeIngestionService.java
    │   │   └── config/
    │   │       └── AppConfig.java
    │   └── resources/
    │       ├── application.properties
    │       └── templates/dashboard.html
    └── test/
        └── java/com/demo/upimesh/
            └── IdempotencyConcurrencyTest.java
```

This lists the main classes; check the source tree for the exact current files.

---

## API reference

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/` | Serve the dashboard |
| `GET` | `/api/server-key` | Return the server's public key |
| `GET` | `/api/accounts` | Return account details and balances (PIN hashes are excluded) |
| `GET` | `/api/transactions` | Return recent transaction records |
| `GET` | `/api/mesh/state` | Return virtual mesh state |
| `POST` | `/api/demo/send` | Create and inject an encrypted demo payment packet |
| `POST` | `/api/mesh/gossip` | Simulate one gossip round |
| `POST` | `/api/mesh/flush` | Upload packets held by bridge devices |
| `POST` | `/api/mesh/reset` | Reset simulated mesh state and its in-memory idempotency cache |
| `POST` | `/api/bridge/ingest` | Process an uploaded packet |

### Bridge ingestion request

Check `ApiController.java` and its request/response models for the exact current headers and response schema. Conceptual request shape:

```json
{
  "packetId": "example-packet-id",
  "ttl": 2,
  "createdAt": 1730000000000,
  "ciphertext": "base64-encoded-encrypted-payload"
}
```

Possible outcomes include settlement, duplicate rejection, or invalid packet rejection. Exact response fields and error reasons are defined by the implementation.

---

## Tests

Run tests from the project root:

```powershell
.\mvnw.cmd test
```

The existing `IdempotencyConcurrencyTest` includes:
- **`encryptDecryptRoundTrip`** — verifies that an encrypted payload can be decrypted.
- **`tamperedCiphertextIsRejected`** — verifies that tampered ciphertext is rejected.
- **`singlePacketDeliveredByThreeBridgesSettlesExactlyOnce`** — delivers the same packet concurrently and checks that settlement occurs only once.

All three existing tests passed in a local run on 17 October 2026. Rerun the command to verify the current code and environment.

Manual dashboard tests also verified a successful payment with the correct demo PIN and rejection of a payment with an incorrect PIN, with balances unchanged for the rejected payment.

---

## Limitations and production considerations

This is an educational prototype, not a real UPI implementation.

- **No real Bluetooth mesh:** the network is simulated in software.
- **No real UPI or bank integration:** settlement updates the demo MySQL ledger only.
- **Server-side demo packet creation:** the demo endpoint simulates the sender rather than using a production mobile app.
- **In-memory idempotency:** duplicate claims are local to one running JVM and do not survive a restart.
- **Ephemeral server keys:** the RSA key pair is generated at startup rather than stored in production key management.
- **No production bridge authentication:** bridge requests need secure identity and authentication before real deployment.
- **Offline double-spend risk:** a sender can create multiple offline payment instructions before the backend receives them. A later payment can be rejected if funds are no longer available.
- **No production banking controls:** real deployment would require security review, rate limiting, monitoring, operational recovery, compliance, and authorized banking/payment infrastructure.

A more accurate description of this concept is **mesh-routed deferred settlement**, rather than real-time offline UPI.

---

## Troubleshooting

### `DB_USERNAME` or `DB_PASSWORD` is missing

Set both variables in the terminal or IDE run configuration that launches the application. PowerShell variables set with `$env:...` last only for that terminal session.

### MySQL connection fails

Check that MySQL is running, the `upi_offline_mesh` database exists, and the process has the correct credentials. Do not share your password in logs, screenshots, or source control.

### Port 8080 is already in use

Stop the other process using the port or change `server.port` in `application.properties`.

### PowerShell does not recognize `mvnw.cmd`

Run the wrapper from the project root with the `.\` prefix:

```powershell
.\mvnw.cmd spring-boot:run
```

### Tests cannot load the Spring application context

Ensure `DB_USERNAME` and `DB_PASSWORD` are available to the same terminal or test-run configuration used to launch Maven, and confirm that MySQL is running.

---

## 👨‍💻 Author
**Pawan Kumar** - JAVA Developer

