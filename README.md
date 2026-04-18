# Subspace Infinity

A cross-platform Subspace client and server written in Java, built for extensibility and modularity.

[![Discord](https://img.shields.io/discord/YOUR_DISCORD_ID?label=Discord&logo=discord)](https://discord.gg/tfyWxbK)

## About Subspace / Continuum

[Subspace Continuum](https://store.steampowered.com/app/352700/Subspace_Continuum/) is a 2D massive-multiplayer online game (MMOG) from the 90s. Anyone can host their own Subspace Zone (server) and allow clients to connect.

## About This Project

Subspace Infinity reimagines the classic game with modern technology:
- **Server**: Modular architecture optimized for scaled, networked, grid-based physics
- **Client**: Built on [JMonkeyEngine](https://jmonkeyengine.org/), a modern game engine

<p align="center">
  <img alt="Screenshot 1" src="screenshots/image.png?raw=true" width="45%">
&nbsp; &nbsp; &nbsp; &nbsp;
  <img alt="Screenshot 2" src="screenshots/image2.png?raw=true" width="45%">
</p>

<p align="center">
  <img alt="Screenshot 3" src="screenshots/image5.png?raw=true" width="45%">
&nbsp; &nbsp; &nbsp; &nbsp;
  <img alt="Screenshot 4" src="screenshots/image4.png?raw=true" width="45%">
</p>

## Quick Start

```bash
# Clone the repository
git clone https://github.com/assofohdz/Subspace-Infinity.git
cd Subspace-Infinity

# Run (requires dependencies to be installed first - see docs/setup-guide.md)
./gradlew :infinity:run
```

## Documentation

| Document | Description |
|----------|-------------|
| [Setup Guide](docs/setup-guide.md) | Complete development environment setup |
| [Quick Reference](docs/quick-reference.md) | Common commands and quick info |
| [Contributing](CONTRIBUTING.md) | Guidelines for contributors |

## Download

**Latest public build**: [Itch.io](https://assofohdz.itch.io/subspace-infinity)

## Community

| Discord Server | Link |
|----------------|------|
| Subspace Infinity | [Join](https://discord.gg/tfyWxbK) |
| Quantum Space | [Join](https://discord.gg/gvtnSAcy) |
| Subspace Continuum | [Join](https://discord.gg/y3AanC8Z) |
| Extreme Games | [Join](https://discord.gg/hY3gjeJ9) |

## Acknowledgments

### Core Frameworks

Built on the excellent work of [Paul Speed](https://github.com/pspeed42) and the [jMonkeyEngine community](https://hub.jmonkeyengine.org/):

| Framework | Purpose |
|-----------|---------|
| [Zay-ES](https://github.com/jMonkeyEngine-Contributions/zay-es) | Entity Component System |
| [SiO2](https://github.com/Simsilica/SiO2) | Game loop and system management |
| [SimEthereal](https://github.com/Simsilica/SimEthereal) | Networking layer |
| [Pager](https://github.com/Simsilica/Pager) | World paging |
| [Lemur](https://github.com/jMonkeyEngine-Contributions/Lemur) | GUI library |
| [MOSS](https://github.com/assofohdz/moss) | Physics, blocks, and world management |

### Subspace Community

Thanks to the original Subspace/Continuum developers, POiD, Grelminar, Gigamon, and all the mapmakers and module developers.

### Related Projects

- [ASSS](https://bitbucket.org/grelminar/asss) - A Small Subspace Server
- [SubspaceServer](https://github.com/gigamon-dev/SubspaceServer) - Gigamon's server
- [Trench Wars Core](https://github.com/Trench-Wars/twcore)
- [Subspace Wikipedia](https://en.wikipedia.org/wiki/SubSpace_(video_game))

## License

See [LICENSE](LICENSE)
