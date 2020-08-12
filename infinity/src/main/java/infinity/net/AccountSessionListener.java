package infinity.net;

import com.jme3.network.service.rmi.Asynchronous;

/**
 * The asynchronous callbacks the server-side account service uses to send
 * information back to the client. A client will register an
 * AccountSessionListener to handle these callbacks.
 *
 * These are especially important to do asynchronously as they might otherwise
 * block other callbacks other services might be waiting to make. Also,
 * synchronous callbacks often contribute to logical deadlocks.
 *
 * @author Paul Speed
 */
public interface AccountSessionListener {

    /**
     * Called by the server to provide login status to the client after a login
     * attempt.
     *
     * @param loggedIn the boolean indicating if the client is logged in
     */
    @Asynchronous
    void notifyLoginStatus(boolean loggedIn);
}
