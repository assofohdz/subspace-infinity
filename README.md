# Examples
Example applications for various Simsilica libraries, singly or in combination.

## simple-jme
A simple JME "blue cube" example using a gradle build file and an asset project setup.

To run: `gradle run`

## zay-es-net-basic
A non-UI, non-graphical, example of using the zay-es-net networking layer for zay-es.

To run the server: `gradle runServer`

To run the client: `gradle runClient`

## network-basic
A base template project for network games.  Provides a simple main menu that includes
options for hosting a local game or connecting to a remote game.  Sets up the client/server
code but otherwise provides absolutely no game logic.  A blank canvas ready for 'game'.

To run: `gradle run`

## sim-eth-basic
A simple space ships 'game' built on JME, Lemur, SpiderMonkey, and the SimEthereal real-time
object synching library.

To run: `gradle run`

## sim-eth-es
A simple space ships 'game' built on JME, Lemur, Zay-ES, Zay-ES-Net, SpiderMonkey, and the SimEthereal real-time
object synching library.  This modifies the sim-eth-basic to be ES based using the Zay-ES library.

To run: `gradle run`
