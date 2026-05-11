# Subspace-Infinity Development Setup Guide

This guide walks you through setting up your development environment to build and run Subspace-Infinity.

## Toolchain Versions

*Last updated: 2026-04-23*

| Tool    | Minimum  | Recommended / CI | Pinned by                        |
|---------|----------|------------------|----------------------------------|
| JDK     | 17       | 21 (Temurin)     | `.github/workflows/main.yml` (`JAVA_VERSION: '21'`) |
| Gradle  | —        | 8.14.5           | `gradle/wrapper/gradle-wrapper.properties` (use `./gradlew`) |
| Git     | any recent | —              | —                                |

Update this table whenever the CI workflow or Gradle wrapper is bumped; other docs reference it as the single source of truth.

## Prerequisites

### Java Development Kit (JDK)

See the [Toolchain Versions](#toolchain-versions) table above for the supported JDK range.

**Ubuntu/Debian:**
```bash
sudo apt install openjdk-21-jdk
```

**Fedora:**
```bash
sudo dnf install java-21-openjdk-devel
```

**macOS (Homebrew):**
```bash
brew install openjdk@21
```

**Windows:**
Download from [Adoptium](https://adoptium.net/) or use a package manager like [Chocolatey](https://chocolatey.org/):
```powershell
choco install temurin21
```

Verify your installation:
```bash
java -version
```

### Git

You'll need Git to clone repositories:
```bash
# Ubuntu/Debian
sudo apt install git

# Fedora
sudo dnf install git

# macOS
brew install git
```

---

## Step 1: Clone Required Dependencies

Subspace-Infinity depends on several libraries that must be built from source and installed to your local Maven repository. Create a working directory:

```bash
mkdir -p ~/dev && cd ~/dev
```

### 1.1 Clipper (Polygon Operations)

Clipper is a polygon clipping library (by jchamlin):

```bash
git clone https://github.com/jchamlin/clipper-java.git
cd clipper-java
./gradlew publishToMavenLocal
cd ..
```

### 1.2 Simsilica Core Libraries

Create a directory for Simsilica libraries:

```bash
mkdir -p simsilica && cd simsilica
```
```bash
# SimMath - Math utilities
git clone https://github.com/Simsilica/SimMath.git
cd SimMath && ./gradlew publishToMavenLocal && cd ..

# SiO2 - Core framework
git clone https://github.com/Simsilica/SiO2.git
cd SiO2 && ./gradlew publishToMavenLocal && cd ..

# SimEthereal - Networking
git clone https://github.com/Simsilica/SimEthereal.git
cd SimEthereal && ./gradlew publishToMavenLocal && cd ..

# Pager - Paging/streaming
git clone https://github.com/Simsilica/Pager.git
cd Pager && ./gradlew publishToMavenLocal && cd ..

# SimFX - Effects
git clone https://github.com/Simsilica/SimFX.git
cd SimFX && ./gradlew publishToMavenLocal && cd ..
```

### 1.3 jMonkeyEngine Contributions
```bash
# Lemur - UI framework
git clone https://github.com/jMonkeyEngine-Contributions/Lemur.git
cd Lemur && ./gradlew publishToMavenLocal && cd ..

# Zay-ES - Entity Component System
git clone https://github.com/jMonkeyEngine-Contributions/zay-es.git
cd zay-es && ./gradlew publishToMavenLocal && cd ..
```

### 1.4 Moss (Physics Library)

Moss is a modular physics/world library. This is a critical dependency:

```bash
git clone https://github.com/assofohdz/moss.git
cd moss
./gradlew publishToMavenLocal
cd ..
```

**Moss modules used by Subspace-Infinity:**

| Module | Purpose |
|--------|---------|
| `mblock` | Block-based world representation |
| `mblock-physb` | Physics bindings for mblock |
| `mworld` | World management |
| `sio2-mblock` | SiO2 integration for mblock |
| `sio2-mphys` | SiO2 integration for physics |
| `bpos` | Block position utilities |
| `crig` | Character rigging |

---

## Step 2: Clone and Build Subspace-Infinity

```bash
cd ~/dev
git clone https://github.com/assofohdz/subspace-infinity.git
cd subspace-infinity
./gradlew build
```

---

## Step 3: Run the Game

```bash
./gradlew :infinity-client:run
```

---

## Verifying Your Setup

### Check Moss Installation
```bash
ls ~/.m2/repository/com/simsilica/ | grep -E "mblock|mworld|mphys|sio2|bpos|crig"
```

You should see directories for each Moss module.

### Check All Dependencies
```bash
./gradlew :infinity-client:dependencies --configuration runtimeClasspath
```

---

## Common Commands

| Task | Command |
|------|---------|
| Build all modules | `./gradlew build` |
| Run the game | `./gradlew :infinity-client:run` |
| Clean build artifacts | `./gradlew clean` |
| List dependencies | `./gradlew :infinity-client:dependencies` |
| Check for updates | `./gradlew dependencyUpdates` |

---

## Troubleshooting

### "Could not resolve com.simsilica:mblock:+"
Moss is not installed in your local Maven repository. Go back to Step 1.4 and run:
```bash
cd ~/dev/simsilica/moss
./gradlew publishToMavenLocal
```

### Java module access errors at runtime
These are handled by JVM arguments in the build configuration. If you encounter them, ensure you're running via Gradle (`./gradlew :infinity-client:run`) rather than directly.

### Build fails with Gradle version errors
Always use the included Gradle wrapper (`./gradlew`) rather than a system-installed `gradle` command. The wrapper pins the project's Gradle version — see [Toolchain Versions](#toolchain-versions).

### Build fails with JDK version errors
Ensure your JDK meets the minimum listed in [Toolchain Versions](#toolchain-versions):
```bash
java -version
```

If you have multiple JDKs installed, set `JAVA_HOME` to match (substitute the major version from the table):
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64  # Linux
export JAVA_HOME=$(/usr/libexec/java_home -v 21)     # macOS
```

---

## IDE Setup

### IntelliJ IDEA
1. Open IntelliJ and select **File > Open**
2. Navigate to the `subspace-infinity` directory and open it
3. IntelliJ will detect the Gradle project and import it
4. Wait for indexing and dependency resolution to complete
5. To run, use the Gradle tool window or create a Run Configuration for `infinity-client:run`

### VS Code
1. Install the **Extension Pack for Java** extension
2. Open the `subspace-infinity` folder
3. The Java extension will detect the Gradle project
4. Use the terminal to run: `./gradlew :infinity-client:run`

---

## Full Setup Script

Copy and run this script to set up everything automatically:

```bash
#!/bin/bash
set -e

# Configuration
DEV_DIR="${HOME}/dev"

echo "=== Subspace-Infinity Full Setup ==="
echo "Installing to: ${DEV_DIR}"
echo ""

# Create directories
mkdir -p "${DEV_DIR}"
cd "${DEV_DIR}"

# 1. Clipper (polygon operations)
echo "[1/10] Building clipper-java..."
if [ ! -d "clipper-java" ]; then
    git clone https://github.com/jchamlin/clipper-java.git
fi
cd clipper-java && ./gradlew publishToMavenLocal && cd ..

# 2. Simsilica libraries
mkdir -p simsilica && cd simsilica

echo "[2/10] Building SimMath..."
if [ ! -d "SimMath" ]; then
    git clone https://github.com/Simsilica/SimMath.git
fi
cd SimMath && ./gradlew publishToMavenLocal && cd ..

echo "[3/10] Building SiO2..."
if [ ! -d "SiO2" ]; then
    git clone https://github.com/Simsilica/SiO2.git
fi
cd SiO2 && ./gradlew publishToMavenLocal && cd ..

echo "[4/10] Building SimEthereal..."
if [ ! -d "SimEthereal" ]; then
    git clone https://github.com/Simsilica/SimEthereal.git
fi
cd SimEthereal && ./gradlew publishToMavenLocal && cd ..

echo "[5/10] Building Pager..."
if [ ! -d "Pager" ]; then
    git clone https://github.com/Simsilica/Pager.git
fi
cd Pager && ./gradlew publishToMavenLocal && cd ..

echo "[6/10] Building SimFX..."
if [ ! -d "SimFX" ]; then
    git clone https://github.com/Simsilica/SimFX.git
fi
cd SimFX && ./gradlew publishToMavenLocal && cd ..

# 3. jMonkeyEngine contributions
echo "[7/10] Building Lemur..."
if [ ! -d "Lemur" ]; then
    git clone https://github.com/jMonkeyEngine-Contributions/Lemur.git
fi
cd Lemur && ./gradlew publishToMavenLocal && cd ..

echo "[8/10] Building Zay-ES..."
if [ ! -d "zay-es" ]; then
    git clone https://github.com/jMonkeyEngine-Contributions/zay-es.git
fi
cd zay-es && ./gradlew publishToMavenLocal && cd ..

# 4. Moss
echo "[9/10] Building Moss..."
if [ ! -d "moss" ]; then
    git clone https://github.com/assofohdz/moss.git
fi
cd moss && ./gradlew publishToMavenLocal && cd ..

# Back to dev directory
cd "${DEV_DIR}"

# 5. Subspace-Infinity
echo "[10/10] Building Subspace-Infinity..."
if [ ! -d "subspace-infinity" ]; then
    git clone https://github.com/assofohdz/subspace-infinity.git
fi
cd subspace-infinity && ./gradlew build

echo ""
echo "=== Setup Complete ==="
echo "To run the game:"
echo "  cd ${DEV_DIR}/subspace-infinity"
echo "  ./gradlew :infinity-client:run"
```

Save this as `setup-subspace.sh`, make it executable, and run:

```bash
chmod +x setup-subspace.sh
./setup-subspace.sh
```

---

## Updating Bundled Dependencies (Maintainers)

The repository includes pre-built dependencies in `libs/m2/` for CI/CD builds. These are Simsilica libraries not available on Maven Central.

### When to Update

Update bundled dependencies when:
- A dependency has a bug fix you need
- You need new features from upstream
- Security updates are available

### Update Workflow

1. **Pull and build the upstream library:**
   ```bash
   cd ~/dev/simsilica
   
   # Example: update Lemur
   cd Lemur
   git pull origin master
   ./gradlew publishToMavenLocal
   cd ..
   
   # Example: update Moss
   cd moss
   git pull origin master
   ./gradlew publishToMavenLocal
   cd ..
   ```

2. **Copy updated libraries to the repo:**
   ```bash
   cd ~/dev/subspace-infinity
   
   # Copy all Simsilica libraries
   cp -r ~/.m2/repository/com/simsilica libs/m2/com/
   ```

3. **Test locally:**
   ```bash
   ./gradlew clean build
   ./gradlew :infinity-client:run
   ```

4. **Commit and push:**
   ```bash
   git add libs/
   git commit -m "Update bundled dependencies"
   git push
   ```

### Bundled Libraries

The following libraries are bundled in `libs/m2/`:

| Library | Repository | Purpose |
|---------|------------|---------|
| Moss | github.com/assofohdz/moss | Physics, block world |
| Lemur | github.com/jMonkeyEngine-Contributions/Lemur | UI framework |
| Zay-ES | github.com/jMonkeyEngine-Contributions/zay-es | Entity system |
| SimMath | github.com/Simsilica/SimMath | Math utilities |
| SiO2 | github.com/Simsilica/SiO2 | Core framework |
| SimEthereal | github.com/Simsilica/SimEthereal | Networking |
| Pager | github.com/Simsilica/Pager | Paging/streaming |
| SimFX | github.com/Simsilica/SimFX | Effects |

### Full Update Script

```bash
#!/bin/bash
set -e

SIMSILICA_DIR="${HOME}/dev/simsilica"
SUBSPACE_DIR="${HOME}/dev/subspace-infinity"

echo "=== Updating all dependencies ==="

cd "${SIMSILICA_DIR}"

# Update and rebuild each library
for lib in SimMath SiO2 SimEthereal Pager SimFX Lemur zay-es moss; do
    echo "Updating ${lib}..."
    cd "${lib}"
    git pull origin master || git pull origin main
    ./gradlew publishToMavenLocal
    cd ..
done

# Copy to subspace-infinity
echo "Copying to subspace-infinity..."
cp -r ~/.m2/repository/com/simsilica "${SUBSPACE_DIR}/libs/m2/com/"

echo "Done! Don't forget to test and commit."
```

---

## Releasing (Maintainers)

### Creating a Release

1. **Update version in the root `build.gradle`** (single source of truth — inherited by all subprojects, no `-SNAPSHOT` suffix):
   ```groovy
   subprojects {
       version = 'X.Y.Z'
   }
   ```

2. **Commit and tag:**
   ```bash
   git add build.gradle
   git commit -m "Bump version to X.Y.Z"
   git tag -a vX.Y.Z -m "Release vX.Y.Z"
   git push && git push origin vX.Y.Z
   ```

3. **GitHub Actions will automatically:**
   - Build native installers for Windows (.exe), Linux (.deb), Mac (.dmg)
   - Create a GitHub Release with all installers
   - Push to itch.io (if `BUTLER_API_KEY` secret is set)

### Required Secrets

Set these in GitHub repo Settings → Secrets → Actions:

| Secret | Description |
|--------|-------------|
| `BUTLER_API_KEY` | itch.io API key for publishing (get from https://itch.io/user/settings/api-keys) |

### Manual Release

To trigger a release manually without a tag:
1. Go to Actions → Release workflow
2. Click "Run workflow"
3. Enter version number
4. Click "Run workflow"
