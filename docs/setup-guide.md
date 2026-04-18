# Subspace-Infinity Development Setup Guide

## Current Environment (April 2026)

### Java
- **Installed**: OpenJDK 21.0.10 (Ubuntu 24.04)
- **Moss**: Targets Java 8 bytecode (`sourceCompatibility = 1.8`)
- **Gradle 8.5**: Requires JDK 17+ to run

#### Moss Build Info
- Location: `~/github/assofohdz/moss`
- Gradle wrapper: 8.5
- Compiles to: Java 8 bytecode
- Should build with JDK 17 or 21

#### If Moss fails to build with Java 21
Try JDK 17:
```bash
sudo apt install openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
cd ~/github/assofohdz/moss && ./gradlew publishToMavenLocal
```

### Gradle
- **Wrapper Version**: 8.5 (defined in `gradle/wrapper/gradle-wrapper.properties`)
- **Global Install**: Not required - use `./gradlew` wrapper
- **Note**: Some plugins (parcl) incompatible with Gradle 8.x

---

## Quick Start (Linux)

```bash
cd /home/assofohdz/github/assofohdz/subspace-infinity
./gradlew :infinity:run
```

---

## Moss Physics Library Setup

Moss is a modular physics/world library by Simsilica (pspeed42). Must be built from source and installed to local Maven repo.

### Moss Repository
```bash
git clone https://github.com/assofohdz/moss.git
cd moss
./gradlew publishToMavenLocal
```

### Moss Modules Used by Subspace-Infinity
| Module | Purpose |
|--------|---------|
| `mblock` | Block-based world representation |
| `mblock-physb` | Physics bindings for mblock |
| `mworld` | World management |
| `sio2-mblock` | SiO2 integration for mblock |
| `sio2-mphys` | SiO2 integration for physics |
| `bpos` | Block position utilities |
| `crig` | Character rigging |

### Verify Moss Installation
```bash
ls ~/.m2/repository/com/simsilica/ | grep -E "mblock|mworld|mphys|sio2|bpos|crig"
```

---

## All Dependencies (Build from Source)

These must be cloned and `publishToMavenLocal` (or `install`):

### 1. Clipper (polygon operations)
```bash
git clone https://github.com/jchamlin/clipper-java
cd clipper-java
./gradlew publishToMavenLocal
```

### 2. Simsilica Libraries
```bash
# SimMath
git clone https://github.com/Simsilica/SimMath.git
cd SimMath && ./gradlew publishToMavenLocal && cd ..

# SiO2
git clone https://github.com/Simsilica/SiO2
cd SiO2 && ./gradlew publishToMavenLocal && cd ..

# SimEthereal (networking)
git clone https://github.com/Simsilica/SimEthereal.git
cd SimEthereal && ./gradlew publishToMavenLocal && cd ..

# Pager
git clone https://github.com/Simsilica/Pager.git
cd Pager && ./gradlew publishToMavenLocal && cd ..

# SimFX
git clone https://github.com/Simsilica/SimFX.git
cd SimFX && ./gradlew publishToMavenLocal && cd ..
```

### 3. jMonkeyEngine Contributions
```bash
# Lemur (UI)
git clone https://github.com/jMonkeyEngine-Contributions/Lemur.git
cd Lemur && ./gradlew publishToMavenLocal && cd ..

# Zay-ES (Entity System)
git clone https://github.com/jMonkeyEngine-Contributions/zay-es.git
cd zay-es && ./gradlew publishToMavenLocal && cd ..
```

### 4. Moss
```bash
git clone https://github.com/assofohdz/moss.git
cd moss && ./gradlew publishToMavenLocal && cd ..
```

---

## Key Version Variables (build.gradle)

```groovy
ext.jmeVersion = '3.7.0-beta1.2.2'
ext.mossVersion = "+"
ext.log4jVersion = '2.24.3'
ext.slf4jVersion = '2.0.16'
```

---

## Common Commands

| Task | Command |
|------|---------|
| Build | `./gradlew build` |
| Run | `./gradlew :infinity:run` |
| Clean | `./gradlew clean` |
| Check dependencies | `./gradlew :infinity:dependencies` |
| Update dependency versions | `./gradlew dependencyUpdates` |

---

## Troubleshooting

### "Could not resolve com.simsilica:mblock:+"
→ Moss not installed. Clone and `publishToMavenLocal`

### Java module access errors
→ JVM args in `infinity/build.gradle`:
```groovy
applicationDefaultJvmArgs = ["--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED"]
```

### Gradle version issues
→ Always use wrapper: `./gradlew` not `gradle`
