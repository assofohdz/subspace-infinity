# Subspace-Infinity Quick Reference

## Location
`/home/assofohdz/github/assofohdz/subspace-infinity`

## Run Commands
- **Run game**: `./gradlew :infinity:run`
- **Build**: `./gradlew build`

## Dependencies from Source
- **Moss**: `https://github.com/assofohdz/moss.git` → `./gradlew publishToMavenLocal`
- **Simsilica libs**: SimMath, SiO2, SimEthereal, Pager, SimFX
- **jME contrib**: Lemur, Zay-ES

## Environment
- Java 21 installed (Ubuntu 24.04)
- Gradle 8.5 (wrapper) - requires JDK 17+
- Moss targets Java 8 bytecode, builds with JDK 17+
- Moss location: `~/github/assofohdz/moss`

## Notes
- Full setup guide in [setup-guide.md](setup-guide.md)
- Moss is required physics library - must build from source
