Version 4 (unreleased)
----------
* Converted ModelViewState to use TimeState instead of calling the TimeSource
    directly.
* Fixed some ship model creation bugs that caused ghost ships to appear when
    connecting after another ship was already connected but out of range.
    

Version 3
----------
* Added ability to configure a server message on the stand-alone
    server but also include a decent default.
* Added a build target that will add a server script tn the distribution 
    for running a stand-alone server.
* Added a "stats" command to the command console of the headless server
    that dumps the connection stats for all currently connected players.
* Added a TimeState for other states that want to get a consistent frame time.    
* Added a debug TimeSequenceState that can pop-up a time sync debug display.
* Added "Resume" menu item to the In-Game menu.
* Added an in-game "Help" pop-up mapping to the F1 key.
* Added Gamepad mapping so "select" button will now open the in-game menu.
* Added Gamepad mapping so the HAT will strafe/elevate. 
    

Version 2
----------
* Added a simple chat service.
* Added ship labels that show the player name.
* Updated the network protocol version to 43.


Version 1
-----------
* Initial public release included basic client and server using
    SimEthereal. 
