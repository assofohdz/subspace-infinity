
Introduction
=============
This is a simple example of using Zay-ES-Net and SpiderMonkey
to create a client-server entity-enabled application.  

The test illustrates only the most basic functionality by 
creating a simple command line server that moves some entities
around on an imaginary 10x10 board.  If they bump into the sides
then they turn a random direction.  Every 5 seconds, a random entity
is removed and another random entity is created.

It is only setup to run from one machine as there is no way to
pass a host address to the client.  You can run as many clients as
you like, though.


Running
=============

To run the server:
* Open a command line
* cd into the zay-es-net-basic directory
* type:
    gradlew runServer
    
To run the client:
* Open a command line
* cd into the zay-es-net-basic directory
* type:
    gradlew runClient
 
The client will terminate on its own after 60 seconds.
 

Design Caveats
===============
I tried to keep at as absolutely basic as possible so some corners
were cut in terms of proper design.  These are especially apparent
on the client where everything is included in one class.  A normal
client will have several app states with the actual 'game client' 
app state not even getting attached until the connection is fully
started (as a result of the client state listener).

In this example, I've simplified things by blocking the thread with
a CountDownLatch.  I do not recommend this in normal practice.


Class Anatomy
==============

**GameServer**: all of the game server code except for the game logic.

**SimpleGameLogic**: the actual 'game' logic that moves the pieces around
on the imaginary board and turns them when they bump into the sides.  This
is also where entities are randomly added and removed every 5 seconds.

**GameClient**: all of the game client code.

**GameConstants**: constants like game name, game port, etc. that are shared
between client and server.

**Position**: a custom component that holds the location and rotation of an
entity.  

**LogUtil**: a utility class to initialize the JUL to sl4fj bridge because I
can't stand JUL and all of the simsilica tools log to slf4j already.


Logging
========
Logging for the client and server can be configured in: 
* src/main/resources/client-log4j2.xml for the client.
* src/main/resources/server-log4j2.xml for the server. 

For example, the networking logging level can be lowered to
provide more detail. (For example, setting it to DEBUG, TRACE,
or ALL.)
