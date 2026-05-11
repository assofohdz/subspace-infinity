# Building

## Prerequisites

- **JDK** — see [Toolchain Versions](docs/setup-guide.md#toolchain-versions) for the supported range
- **Dependencies** installed to local Maven repo

## Quick Start

```bash
./gradlew :infinity-client:run
```

## Full Setup

For complete setup instructions including all dependencies:

👉 **[docs/setup-guide.md](docs/setup-guide.md)**

## Common Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build the project |
| `./gradlew :infinity-client:run` | Run the game (Linux/Windows default) |
| `./gradlew :infinity-client:runMac` | Run on macOS (`-XstartOnFirstThread` + headless AWT) |
| `./gradlew :infinity-client:runX11` | Run on Linux/Wayland with X11 backend |
| `./gradlew clean` | Clean build artifacts |
| `./gradlew dependencyUpdates` | Check for dependency updates |
