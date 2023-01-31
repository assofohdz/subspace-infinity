/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

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
import com.simsilica.es.client.EntityDataClientService;
import com.simsilica.ethereal.EtherealClient;
import com.simsilica.ethereal.TimeSource;
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

/**
 *  Manages the connection and game client when connected to a server.
 *
 *  @author    Paul Speed
 */
public class ConnectionState extends CompositeAppState {

    static Logger log = LoggerFactory.getLogger(ConnectionState.class);

    private AppState parent;

    private String host;
    private int port;

    private GameClient client;
    private ConnectionObserver connectionObserver = new ConnectionObserver();
    private Connector connector;
    private Thread renderThread;

    private OptionPanel connectingPanel;

    private volatile boolean closing;

    public ConnectionState( AppState parent, String host, int port ) {
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

    public <T extends ClientService> T getService( Class<T> type ) {
        return client.getService(type);
    }

    public void disconnect() {
        log.info("disconnect()");
        closing = true;
        log.info("Detaching ConnectionState");
        getStateManager().detach(this);
    }

    public boolean join( String userName ) {
        log.info("join(" + userName + ")");

        if( userName != null ) {
            userName = userName.trim();
        }

        if( Strings.isNullOrEmpty(userName) ) {
            showError("Join Error", "Please specify a player name for use in game.", null, false);
            return false;
        }

        // So here we'd login and then when we get a response from the
        // server that we are logged in then we'd launch the game state and
        // so on... for now we'll just do it directly.
        client.getService(AccountClientService.class).login(userName);

        return true;
    }

    protected void onLoggedOn( boolean loggedIn ) {
        if( !loggedIn ) {
            // We'd want to present an error... but right now this will
            // never happen.
        }
        addChild(new GameSessionState(), true);
    }

    @Override
    protected void initialize( Application app ) {

        connectingPanel = new OptionPanel("Connecting...", new ExitAction("Cancel", true));
        getState(OptionPanelState.class).show(connectingPanel);

        this.renderThread = Thread.currentThread();
        connector = new Connector();
        connector.start();
    }

    @Override
    protected void cleanup( Application app ) {
        closing = true;
        if( client != null ) {
            client.close();
        }

        // Close the connecting panel if it's still open
        closeConnectingPanel();

        // And re-enable the parent
        parent.setEnabled(true);
    }

    protected void closeConnectingPanel() {
        if( getState(OptionPanelState.class).getCurrent() == connectingPanel ) {
            getState(OptionPanelState.class).close();
        }
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

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
                if( m != null ) {
                    m += "\n";
                } else {
                    m = "";
                }
                m += e.getClass().getSimpleName() + ":" + e.getMessage();
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
        log.info("Connection established:" + client);
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

        // Add our client listeners
        AccountObserver obs = new AccountObserver();
        AccountClientService serv = client.getService(AccountClientService.class);
        serv.addAccountSessionListener(obs);

        String serverInfo = client.getService(AccountClientService.class).getServerInfo();

        log.debug("Server info:" + serverInfo);

        getStateManager().attach(new LoginState(serverInfo));
    }

    protected void onDisconnected( DisconnectInfo info ) {
        log.info("onDisconnected(" + info + ")");
        closeConnectingPanel();
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

        public ExitAction( boolean close ) {
            this("Ok", close);
        }

        public ExitAction( String name, boolean close ) {
            super(name);
            this.close = close;
        }

        public void execute( Button source ) {
            if( close ) {
                disconnect();
            }
        }
    }

    private class ConnectionObserver implements ClientStateListener, ErrorListener<Client> {
        public void clientConnected( final Client c ) {
            log.info("clientConnected(" + c + ")");
            getApplication().enqueue(new Callable<Object>() {
                public Object call() {
                    onConnected();
                    return null;
                }
            });
        }

        public void clientDisconnected( final Client c, final DisconnectInfo info ) {
            log.info("clientDisconnected(" + c + ", " + info + ")");
            getApplication().enqueue(new Callable<Object>() {
                public Object call() {
                    onDisconnected(info);
                    return null;
                }
            });
        }

        public void handleError( Client source, Throwable t ) {
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
        }

        public void run() {

            try {
                log.info("Creating game client for:" + host + " " + port);
                GameClient client = new GameClient(host, port);
                if( closing ) {
                    return;
                }
                setClient(client);
                client.getClient().addClientStateListener(connectionObserver);
                client.getClient().addErrorListener(connectionObserver);
                if( closing ) {
                    return;
                }

                log.info("Starting client...");
                client.start();
                log.info("Client started.");
            } catch( IOException e ) {
                if( closing ) {
                    return;
                }
                showError("Error Connecting", e, true);
            }
        }
    }
}

