# Client/GUI: HUD display

Status: ready-for-human
Cross-ref: [GH #45](https://github.com/assofohdz/subspace-infinity/issues/45)
Labels: area:client, backlog

Implement a basic player HUD:

On the server, there should be a system keeping track of player stats.

On the client, create a hud state that uses the `HostState.java` to retrieve the above system and get the stats displayed.

- [x] Location/Position (bin/area and world coordinates)
- [ ] Energy / health
- [ ] Freq / team
- [ ] Flag info (maybe just how many flags are owned by client freq)
- [ ] Bounty (goes up with kills or greens)
- [ ] Timer (only needed if a module loads a timer)
- [ ] Spacebucks (score)

## Comments
