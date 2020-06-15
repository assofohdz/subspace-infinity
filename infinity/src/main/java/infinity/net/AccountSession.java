package infinity.net;

import com.jme3.network.service.rmi.Asynchronous;

/**
 * A client's view of the account related services on the server.
 *
 * @author Paul Speed
 */
public interface AccountSession {

    /**
     * Returns information about the server. Currently this is just a
     * description. It would be better to split this into an asynchronous
     * request but this is way simpler. This could be expanded to include
     * capabilities, accepted password hashes, and so on.
     * @return returns the server information
     */
    public String getServerInfo();

    /**
     * Called by the client to provide the player name for this connection and
     * "login" to the game. The server will respond asynchronously with a
     * notifyLoginStatus() to the client's AccountSessionListener. Note: this
     * could have been done synchronously but synchronous calls should generally
     * be avoided when they can. a) it prevents odd logic deadlocks if one isn't
     * careful, and b) it makes user interfaces automatically more responsive
     * without having to write special background worker code. When possible, go
     * asynchronous.
     *
     * @param playerName the name of the player
     */
    @Asynchronous
    public void login(String playerName);
}
