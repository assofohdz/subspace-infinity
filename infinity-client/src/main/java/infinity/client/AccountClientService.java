/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity.client;

import infinity.net.AccountSession;
import infinity.net.AccountSessionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.*;

import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;
import com.jme3.network.service.rmi.RmiClientService;


/** Client-side RMI proxy for the server's {@link AccountSession} (login, server info). */
public class AccountClientService extends AbstractClientService
    implements AccountSession {

  static Logger log = LoggerFactory.getLogger(AccountClientService.class);

  private RmiClientService rmiService;
  private AccountSession delegate;

  private AccountSessionCallback sessionCallback = new AccountSessionCallback();
  private List<AccountSessionListener> listeners = new CopyOnWriteArrayList<>();

  public AccountClientService() {
  }

  @Override
  public String getServerInfo() {
    return delegate.getServerInfo();
  }

  @Override
  public void login( String playerName ) {
    delegate.login(playerName);
  }

  // Called on the networking thread; not safe for visualization mutations.
  public void addAccountSessionListener( AccountSessionListener l ) {
    listeners.add(l);
  }

  public void removeAccountSessionListener( AccountSessionListener l ) {
    listeners.remove(l);
  }

  @Override
  protected void onInitialize( ClientServiceManager s ) {
    log.debug("onInitialize({})", s);
    this.rmiService = getService(RmiClientService.class);
    if( rmiService == null ) {
      throw new IllegalStateException("AccountClientService requires RMI service");
    }
    log.debug("Sharing session callback.");
    rmiService.share(sessionCallback, AccountSessionListener.class);
  }

  @Override
  public void start() {
    log.debug("start()");
    super.start();
    this.delegate = rmiService.getRemoteObject(AccountSession.class);
    log.debug("delegate:{}", delegate);
    if( delegate == null ) {
      throw new IllegalStateException("No account session found during connection setup");
    }
  }

  // Shared with the server over RMI for notifications.
  private class AccountSessionCallback implements AccountSessionListener {

    @Override
    public void notifyLoginStatus( boolean loggedIn ) {
      log.trace("notifyLoginStatus({})", loggedIn);
      for( AccountSessionListener l : listeners ) {
        l.notifyLoginStatus(loggedIn);
      }
    }

  }
}
