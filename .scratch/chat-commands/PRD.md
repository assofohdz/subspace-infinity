# Client/player: Chat commands from player perspective

Status: ready-for-human
Cross-ref: [GH #21](https://github.com/assofohdz/subspace-infinity/issues/21)

**Situation:** Currently no player commands or macros exist.

**Ask:** Implement the commands below that make sense in a new server/client setup.

## Player commands

| Command | Description |
|---------|-------------|
| `?usage` | displays your current usage information |
| `?sheep` | display sheep message and play sheep sound |
| `?buy` | displays list of things which can be bought |
| `?buy <item>` | buys the specified item, ex `?buy repel` |
| `?userid` | displays your unique user id |
| `?owner` | displays name of arena owner (see `server.cfg` Owner:Name) |
| `?packetloss` | displays your current packetloss percentages |
| `?getsettings` | if you own the arena or have sysop powers, this allows you to change settings (same as ESC-C) |
| `?setlevel` | if you own the arena or are a sysop, allows you to change the level/map |
| `?arena` | displays list of public arenas (private ones too if you are sysop) |
| `?time` | displays amount of time remaining in current game (timed games) |
| `?crown` | displays how many kills you have left/need for a crown |
| `?chat[name]` | set your chat channel to 'name' ex `?chat omega` |
| `?best` | displays your personal best in timed games |
| `?flags` | displays who is carrying flags |
| `?team` | displays team-mates of player ticked in stat box |
| `?score` | show score in a soccer game |
| `?ignore [who]` | ignores ticked player or 'who' if specified |
| `?log [filename]` | logs all messages to file specified (default name used if not); type `?log` again to stop |
| `?target[x]` | sets your target bounty |
| `?status` | displays status of your ship |
| `?kill` | causes kill messages to be logged to message area |
| `?ping` | displays your round-trip ping time to the server |
| `?savemap [name.bmp]` | saves a bitmap of current map to specified file (default name used if not) |
| `?go [name]` | go to next public arena, or a private arena if 'name' specified |
| `?lines[xxx]` | show/set number of message lines |
| `?zone [zone name]` | tells you which zone you are in or take you to specified zone |
| `?spec` | display a list of who is specating you |
| `?password pw` | change your personal password to `pw` |
| `?squadjoin name:password` | join an existing squad |
| `?squadcreate name:password` | create a new squad |
| `?squadleave` | leave your existing squad |
| `?squaddissolve` | completely get rid of squad |
| `?squadpassword pw` | change squad password to `pw` |
| `?squadkick playername` | kick `playername` off of squad |
| `?away [message]` | sets/clears an away message |
| `?lines[x]` | sets number of displayed message lines |
| `?namelen[x]` | sets length of name portion in message area |
| `?music[x]` | sets music volume (0 to 10) |
| `?loadmacro` | load message macros |
| `?savemacro` | save message macros |
| `?setsettings` | used to upload a modified .set file (as downloaded via `?getsettings`) — not recommended method |
| `?recycle` | allows arena owner to recycle the arena |
| `?cheater (name)` | sends message to all online mods/smods/sysops and people logged into BanG (1.34.3+) |
| `?getnews` | downloads news.txt to your SS folder (1.34.2+) |
| `?scorereset` | scoreresets yourself (1.34.4+) |
| `?lag` | display both ping and ploss at once (1.34.5+) |
| `?squadowner (squad name)` | tells owner of squad |
| `?squadgrant (player name)` | gives ownership to that player |
| `?squad (player name)` | tells you what squad they are currently in |
| `?find (player name)` | tells you what zone they are in or not online |
| `?get X:Y` | get a setting in .cfg in section X, variable Y |
| `?set X:Y:Z` | set a setting in .cfg in section X, variable Y, value Z |
| `?obscene` | toggles if you view obscene messages or not |
| `?getfile [filename]` | same as sysop's `*getfile` (1.34.5+) |
| `?putfile [filename]` | same as sysop's `*putfile` (1.34.5+) |
| `?squadlist` | lists all players on your squad (squadowner only command, SSC billing only) |
| `?sound[x]` | change in-game sounds (Continuum) |
| `?enter` | toggles player entering arena msgs in middle/chat/no where (Continuum) |
| `?leave` | toggles player leaving arena msgs in middle/chat/no where (Continuum) |
| `?logbuffer [filename]` | starts a log, and will include all messages you have received since you logged into zone (Continuum) |
| `?message name:message` | leave a message to that player (SSC Billing only) |
| `?messages` | read all messages sent to you (SSC Billing only) |
| `?nopubchat` | toggles viewing public messages (Continuum .37) |

## Player macros

| Macro | Description |
|-------|-------------|
| `%red` | name(bty:flags) of nearest enemy flag carrier |
| `%redname` | name of nearest enemy flag carrier |
| `%redbounty` | bounty of nearest enemy flag carrier |
| `%redflags` | number of flags of nearest enemy flag carrier |
| `%tickname` | ticked name |
| `%selfname` | your name |
| `%squad` | your squad |
| `%freq` | your frequency |
| `%bounty` | your bounty |
| `%flags` | your flag count |
| `%energy` | your energy |
| `%shield` | your shield time |
| `%super` | your super time |
| `%killer` | last person to kill you |
| `%killed` | last person you killed |
| `%coord` | your coords (e.g. A4, J12) |
| `%area` | your area (e.g. Upper-Right, Middle) |

## Comments
