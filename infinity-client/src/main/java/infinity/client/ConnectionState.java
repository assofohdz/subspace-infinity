// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client;

import com.google.common.base.Strings;
import com.jme3.app.Application;
import com.jme3.app.state.AppState;
import com.jme3.network.Client;
import com.jme3.network.ClientStateListener;
import com.jme3.network.ClientStateListener.DisconnectInfo;
import com.jme3.network.ErrorListener;
import com.jme3.network.service.ClientService;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.client.EntityDataClientService;
import com.simsilica.ethereal.EtherealClient;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.event.EventBus;
import com.simsilica.lemur.Action;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.OptionPanel;
import com.simsilica.lemur.OptionPanelState;
import com.simsilica.state.CompositeAppState;
import infinity.client.states.LoginState;
import infinity.net.AccountSessionListener;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the connection and game client when connected to a server. */
public class ConnectionState extends CompositeAppState {

    static Logger log = LoggerFactory.getLogger(ConnectionState.class);

    private AppState parent;

    private String host;
    private int port;

    private GameClient client;
    private ConnectionObserver connectionObserver = new ConnectionObserver();
    private Thread renderThread;

    private OptionPanel connectingPanel;

    private volatile boolean closing;

    public ConnectionState( final AppState parent, final String host, final int port ) {
        this.parent = parent;
        this.host = host;
        this.port = port;
    }

    public int getClientId() {
        return client.getClient().getId();
    }

    public TimeSource getRemoteTimeSource() {
        return getService(EtherealClient.class).getTimeSource();
    }

    public EntityData getEntityData() {
        return getService(EntityDataClientService.class).getEntityData();
    }

    public <T extends ClientService> T getService( final Class<T> type ) {
        return client.getService(type);
    }

    public void disconnect() {
        log.info("disconnect()");
        closing = true;
        log.info("Detaching ConnectionState");
        // Clean logout — carry the player entity if a session was bound (mirrors sessionStarted shape).
        final GameSessionClientService sessionSvc = client.getService(GameSessionClientService.class);
        final EntityId playerEntity = sessionSvc != null ? sessionSvc.getPlayer() : null;
        EventBus.publish(ClientEvent.sessionEnded, ClientEvent.forSession(playerEntity, getClientId()));
        getStateManager().detach(this);
    }

    public boolean join( final String userName ) {
        log.info("join({})", userName);

        String name = userName;
        if( name != null ) {
            name = name.trim();
        }

        if( Strings.isNullOrEmpty(name) ) {
            showError("Join Error", "Please specify a player name for use in game.", null, false);
            return false;
        }

        // So here we'd login and then when we get a response from the
        // server that we are logged in then we'd launch the game state and
        // so on... for now we'll just do it directly.
        client.getService(AccountClientService.class).login(name);

        return true;
    }

    protected void onLoggedOn( final boolean loggedIn ) {
        // No error path yet — login currently can't fail server-side.
        addChild(new GameSessionState(), true);
        final EntityId playerEntity = client.getService(GameSessionClientService.class).getPlayer();
        EventBus.publish(ClientEvent.sessionStarted, ClientEvent.forSession(playerEntity, getClientId()));
    }

    @Override
    protected void initialize( final Application app ) {

        connectingPanel = new OptionPanel("Connecting...", new ExitAction("Cancel", true));
        getState(OptionPanelState.class).show(connectingPanel);

        this.renderThread = Thread.currentThread();
        final Connector connector = new Connector();
        connector.start();
    }

    @Override
    protected void cleanup( final Application app ) {
        closing = true;
        if( client != null ) {
            client.close();
        }

        // Close the connecting panel if it's still open
        closeConnectingPanel();

        // And re-enable the parent
        parent.setEnabled(true);
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals") // OptionPanel identity: only close if it's still our panel
    protected void closeConnectingPanel() {
        if( getState(OptionPanelState.class).getCurrent() == connectingPanel ) {
            getState(OptionPanelState.class).close();
        }
    }

    @Override
    protected void onEnable() {
        // no-op: BaseAppState lifecycle slot; connect lifecycle drives this state, not enable/disable
    }

    @Override
    protected void onDisable() {
        // no-op: BaseAppState lifecycle slot; disconnect lifecycle drives this state, not enable/disable
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals") // canonical Thread identity check
    protected boolean isRenderThread() {
        return Thread.currentThread() == renderThread;
    }

    protected void showError( final String title, final Throwable e, final boolean fatal ) {
        showError(title, null, e, fatal);
    }

    protected void showError( final String title, final String message, final Throwable e, final boolean fatal ) {
        if( isRenderThread() ) {
            String m = message;
            if( e != null ) {
                final StringBuilder sb = new StringBuilder();
                if( m != null ) {
                    sb.append(m).append('\n');
                }
                sb.append(e.getClass().getSimpleName()).append(':').append(e.getMessage());
                m = sb.toString();
            }
            getState(OptionPanelState.class).show(title, m, new ExitAction(fatal));
        } else {
            getApplication().enqueue(new Callable<Object>() {
                public Object call() {
                    showError(title, e, fatal);
                    return null;
                }
            });
        }
    }

    protected void setClient( final GameClient client ) {
        log.info("Connection established:{}", client);
        if( isRenderThread() ) {
            this.client = client;
        } else {
            getApplication().enqueue(new Callable<Object>() {
                public Object call() {
                    setClient(client);
                    return null;
                }
            });
        }
    }

    protected void onConnected() {
        log.info("onConnected()");
        closeConnectingPanel();

        // Publish transport-up event before LoginState attaches so listeners can wire dependent state.
        EventBus.publish(ClientEvent.clientConnected, ClientEvent.forTransport(client.getClient().getId()));

        // Add our client listeners
        AccountObserver obs = new AccountObserver();
        AccountClientService serv = client.getService(AccountClientService.class);
        serv.addAccountSessionListener(obs);

        String serverInfo = client.getService(AccountClientService.class).getServerInfo();

        log.debug("Server info:{}", serverInfo);

        getStateManager().attach(new LoginState(serverInfo));
    }

    protected void onDisconnected( final DisconnectInfo info ) {
        log.info("onDisconnected({})", info);
        closeConnectingPanel();
        EventBus.publish(ClientEvent.clientDisconnected, ClientEvent.forTransport(getClientId()));
        if( closing ) {
            return;
        }
        if( info != null ) {
            showError("Disconnect", info.reason, info.error, true);
        } else {
            showError("Disconnected", "Unknown error", null, true);
        }
    }

    private class ExitAction extends Action {
        private boolean close;

        public ExitAction( final boolean close ) {
            this("Ok", close);
        }

        public ExitAction( final String name, final boolean close ) {
            super(name);
            this.close = close;
        }

        public void execute( final Button source ) {
            if( close ) {
                disconnect();
            }
        }
    }

    private class ConnectionObserver implements ClientStateListener, ErrorListener<Client> {
        public void clientConnected( final Client c ) {
            log.info("clientConnected({})", c);
            getApplication().enqueue(new Callable<Object>() {
                public Object call() {
                    onConnected();
                    return null;
                }
            });
        }

        public void clientDisconnected( final Client c, final DisconnectInfo info ) {
            log.info("clientDisconnected({}, {})", c, info);
            getApplication().enqueue(new Callable<Object>() {
                public Object call() {
                    onDisconnected(info);
                    return null;
                }
            });
        }

        public void handleError( final Client source, final Throwable t ) {
            log.error("Connection error", t);
            showError("Connection Error", t, true);
        }
    }

    private class AccountObserver implements AccountSessionListener {

        public void notifyLoginStatus( final boolean loggedIn ) {
            getApplication().enqueue(new Callable<Object>() {
                public Object call() {
                    onLoggedOn(loggedIn);
                    return null;
                }
            });
        }
    }

    private class Connector extends Thread {

        public Connector() {
            // no-op: thread fields are set by enclosing connect() before start()
        }

        public void run() {

            try {
                log.info("Creating game client for:{} {}", host, port);
                GameClient newClient = new GameClient(host, port);
                if( closing ) {
                    return;
                }
                setClient(newClient);
                newClient.getClient().addClientStateListener(connectionObserver);
                newClient.getClient().addErrorListener(connectionObserver);
                if( closing ) {
                    return;
                }

                log.info("Starting client...");
                newClient.start();
                log.info("Client started.");
            } catch( final IOException e ) {
                if( closing ) {
                    return;
                }
                showError("Error Connecting", e, true);
            }
        }
    }
}
