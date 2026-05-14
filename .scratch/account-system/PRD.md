# Server/Account: Implement account system

Status: ready-for-human
Cross-ref: [GH #62](https://github.com/assofohdz/subspace-infinity/issues/62)
Labels: area:server, backlog

See https://github.com/assofohdz/moss/tree/master/maccount and implement an account system that keeps track of players and their accounts. Can keep login to commands for now instead of GUI.

- [ ] Server loads accounts and their assigned privileges upon startup (see http://wiki.minegoboom.com/index.php/Server_Setup)
- [ ] Ability to log in
- [ ] Ability to log out

## Code-extracted TODOs

- [ ] Implement the `infinity.sim.AccountManager` interface (currently an empty marker) — needs per-entity access-level lookup so chat command dispatch can gate execution. Source: `infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java:193`.

## Comments
