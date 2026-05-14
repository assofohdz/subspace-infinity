# Server/security: Access roles

Status: ready-for-human
Cross-ref: [GH #19](https://github.com/assofohdz/subspace-infinity/issues/19)
Labels: enhancement, area:server

**Situation:** Currently there's no implementation of login or security access levels.

**Ask:** Look into and design some of the most necessary access levels.

As inspiration: taken from minegoboom's website: http://www.minegoboom.com/server/commands.html — see also http://wiki.minegoboom.com/index.php/Server_Setup.

## SYSOP commands (selected)

| Command | Description |
|---------|-------------|
| `*stat` | Displays server statistics |
| `*addword` | Adds word to the obscene list |
| `*shutdown [exename]` | Shuts down server (terminates process), optionally runs exename.exe |
| `*s*` | Set `server.ini` variable, ex `*s*Misc:MaxPlayers:4` |
| `*g*` | Get `server.ini` variable, ex `*g*Misc:MaxPlayers` |
| `*log` | Display a log of messages printed on server screen |
| `*energy` | Toggles viewing other players' energy levels (privately = lets target see their own) |
| `*addmachine (id)` | Adds machineID# to `idblock.txt` (1.34.5+) |
| `*removemachine (id)` | Removes id# from `idblock.txt` (1.34.5+) |
| `*listmachine` | Lists all id# from `idblock.txt` (1.34.5+) |
| `*ufo` | Give yourself UFO ship (toggle, 1.34.1+) |
| `*super` | Give yourself the super ship with everything (1.34.1+) |
| `*mirror` | Shows what everyone sees you as (1.34.1+) |
| `*getfile (name)` | Downloads file from server (1.34.5+) |
| `*putfile (name)` | Uploads file from server (1.34.5+, restricted by extension) |
| `*delfile (name)` | Deletes file from server (1.34.5+, restricted by extension) |
| `*version` | Tells what subgame version it is (1.34.6+) |
| `*lag` | List all player's quick lag info (1.34.8+) |

### Privately-sent SYSOP commands

| Command | Description |
|---------|-------------|
| `*sysop` | Grants temporary sysop privileges to the target |
| `*smoderator` | Grants temporary super-moderator privileges |
| `*thor #` | Sets player's thor level to # |
| `*lowbandwidth #` | Toggles double-packet sending to fight lag (1.34.1+) |
| `*messagelogging #` | Detects if a player is logging messages (1.34.4+) |
| `*super` / `*ufo` | Gives target a super ship / UFO ship (1.34.4+) |
| `*einfo` | Display target's userid + screen rez (1.34.5+) |
| `*bandwidth #` | Override target's CutbackWatermark (1.34.11h+) |
| `*points #` | Add (or subtract) points to player. **Warning:** sets all points to flag points (1.34.11+) |

## SUPER MODERATOR commands

| Command | Description |
|---------|-------------|
| `*szone` | Sends message to all zones with same scoreid as current zone |
| `*zone` | Send message to all arenas in this zone |
| `*getlist` / `*putlist` | Get/set list of permitted players (`permit.txt`) |
| `*getmodlist` / `*putmodlist` | Get/set moderator list (`moderate.txt`) |
| `*recycle` | Recycles the server (kicks everybody off) |
| `*restart` | Restarts timed games (speed zone) |
| `*prize` | Grant all ships random prizes (or specific prize if `*prize #`) |
| `*listmod` | Display all mods/smods/sysops currently logged in (1.34.4+) |

### Privately-sent SMOD commands

| Command | Description |
|---------|-------------|
| `*info` | Displays lots of player info |
| `*where` | Tells location of player |
| `*trace` | Server-side tracert (subgame 1.34.1+ named subgame2.exe) |
| `*moderator` | Make target a moderator for the session |

## MODERATOR commands

| Command | Description |
|---------|-------------|
| `*arena` | Send message to this arena |
| `*permit` / `*revoke` | Grant/revoke restricted-zone access |
| `*beginlog [text]` / `*endlog` | Begin/end logging (auto-sends log on end) |
| `*shipreset` | Reset all ships to 0 bounty (private = single target) |
| `*scorereset` | Reset stats (private = single target, public = all) |
| `*flagreset` | Reset the flag game |
| `*timereset` | Reset timer on timed game |
| `*banner` | Turn on your banner regardless of point requirements |
| `*lock` / `*lockspec` | Lock so nobody can enter (toggle); spec-only variant |
| `*lockteam` / `*lockprivate` / `*lockpublic` / `*lockchat` / `*lockall` | Lock specific message scopes |
| `*timer` | Start a notifying timer |
| `*kill [minutes]` | Kick player off (banning for specified minutes optionally) |
| `*shutup` | Prevent target from talking (use with care — can lock yourself) |
| `*spec` / `*specall` | Force player(s) into spec |
| `*setship #` | Set target's ship (1.34.2+) |
| `*setfreq ####` | Set target's frequency (1.34.2+) |
| `*locate (name)` | Tells you which arena player is in (1.34.2+) |
| `*watchgreen` | See every green pickup as target gets it (1.34.5+) |
| `*listban` / `*removeban (ID#)` | Manage `*kill` bans (1.34.5+) |
| `*lag` | Show ploss + ping (1.34.5+; .6+ has weapon ploss) |
| `*greeninfo` | Display green ID# and counts since last shipreset (1.34.9+) |
| `*flags` | Display flag's coords (1.34.9+) |
| `*warn [text]` | Privately msg player with mod sound + warning prefix (1.34.10+) |
| `*warpto [X] [Y]` | Warp player to specific X,Y (1.34.10+, Continuum .36) |
| `*relkills #` | Force reliable kills regardless of bounty (1.34.11h+) |
| `*tinfo` | Display target's time history (1.34.12pr2+) |
| `*watchdamage` | Toggle viewing target's damage info (1.34.12pr4+, Continuum .37+B10) |

## Power-level rules

- **Sysops** have all powers above.
- **Super-Moderators** have all powers above except SYSOP.
- **Moderators** have only the moderator commands.

When a Sysop/SMod gives temporary powers to a player, that player gains abilities but no sounds. To grant sound access, add the player's name to `moderator.txt`.

A lower power class cannot use `*spec`, `*shutup`, or `*kill` on a higher power class. Sysops can `*moderator` an SMod to temporarily cancel their powers.

## Code-extracted TODOs

- [ ] Gate chat command dispatch on `AccountHostedService.isAtLeastAtAccessLevel(fromEntity, cc.getAccessLevelRequired())` — the call site is already prepared, body is commented out pending the account service. Source: `infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java:193`.
- [ ] `registerPatternTriConsumer` broadcasts the command help text to every connected player on registration — should only post to players whose access level meets the command's requirement. Source: `infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java:252`.
- [ ] `registerCommandConsumer` broadcasts the command help text to every connected player on registration — same access-level filter needed. Source: `infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java:303`.

## Comments
