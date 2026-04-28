# Movement: Implement an AttachSystem and AttachState

Status: ready-for-human
Cross-ref: [GH #15](https://github.com/assofohdz/subspace-infinity/issues/15)
Labels: enhancement, help wanted, area:server

**Situation:** Currently there's no way to attach to another player.

**Ask:** Implement a system for handling attaching to another player as a turret. Effectively the attaching player latches on and becomes a turret on the moving player.

Would require a server side System to handle attach limitations and taking over the movement based on the target player. The `SettingsSystem` can be asked for:

- Thrust penalty
- Speed penalty
- Limit

**Phase 1:** Start by using the chat interface to attach to another player.

**Phase 2:** Select the moving player in a list of players that are currently online and on your frequency (team).

From Wikipedia:
> Ships may also attach to other friendly ships. In this scenario, the attaching ship loses thrust control and becomes a weapons turret on the back of another ship. This is technically achieved by performing a warp, thus requiring full energy to attach and draining energy in the process. A turret ship takes damage like a normal ship and may detach at any time. In addition, a ship carrying turrets may detach one or all of them at any time.

## Comments
