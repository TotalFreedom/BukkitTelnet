package me.totalfreedom.bukkittelnet.api;

import java.util.List;
import me.totalfreedom.bukkittelnet.SocketListener;
import me.totalfreedom.bukkittelnet.TelnetConfigLoader.TelnetConfig;
import me.totalfreedom.bukkittelnet.session.ClientSession;

public interface Server
{

    public void startServer();

    public void stopServer();

    @Deprecated
    public SocketListener getSocketListener();

    public TelnetConfig getConfig();

    public List<ClientSession> getSessions();

}
