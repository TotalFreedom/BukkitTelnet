package me.StevenLawson.BukkitTelnet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class BT_ClientSession extends Thread
{
    public enum FilterMode
    {
        FULL, NONCHAT_ONLY, CHAT_ONLY
    }
    private static final Pattern NONASCII_FILTER = Pattern.compile("[^\\x20-\\x7E]");
    private static final Pattern AUTH_INPUT_FILTER = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern COMMAND_INPUT_FILTER = Pattern.compile("^[^a-zA-Z0-9/\\?!\\.]+");
    //
    private final Socket clientSocket;
    private final String clientAddress;
    //
    public FilterMode filter_mode = FilterMode.FULL;
    private SessionLogHandler sessionLogHandler;
    private SessionCommandSender sessionCommandSender;
    private BufferedWriter writer;
    private BufferedReader reader;
    private String userName;
    private boolean hasTerminated = false;

    public BT_ClientSession(Socket clientSocket)
    {
        this.clientSocket = clientSocket;
        this.clientAddress = this.clientSocket.getInetAddress().getHostAddress();
        this.userName = this.clientAddress;

        BT_Log.info("Client connected: " + this.clientAddress);
    }

    public boolean isConnected()
    {
        synchronized (this.clientSocket)
        {
            return !this.clientSocket.isClosed();
        }
    }

    public void terminateSession()
    {
        if (this.hasTerminated)
        {
            return;
        }

        this.hasTerminated = true;

        BT_Log.info("Closing connection: " + this.clientAddress);

        if (this.sessionLogHandler != null)
        {
            BT_Log.getLogger().removeHandler(this.sessionLogHandler);
        }

        synchronized (this.clientSocket)
        {
            if (this.clientSocket != null)
            {
                try
                {
                    writeOutFormatted("Closing connection.", true);
                    this.clientSocket.close();
                }
                catch (Exception ex)
                {
                }
            }
        }
    }

    public String getUserName()
    {
        return this.userName;
    }

    public void writeOut(String message)
    {
        if (this.writer != null && this.isConnected())
        {
            try
            {
                this.writer.write(ChatColor.stripColor(message));
                this.writer.flush();
            }
            catch (IOException ex)
            {
            }
        }
    }

    public void writeOutFormatted(String message, boolean newLine)
    {
        writeOut("[BukkitTelnet (Console)]: " + message + (newLine ? "\r\n:" : ""));
    }

    public void flush()
    {
        if (this.writer != null && this.isConnected())
        {
            try
            {
                this.writer.flush();
            }
            catch (IOException ex)
            {
            }
        }
    }

    public void sendBukkitCommand(final String command)
    {
        final CommandSender sender = getCommandSender();

        try
        {
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    final Server server = Bukkit.getServer();

                    final TelnetCommandEvent telnetEvent = new TelnetCommandEvent(sender, command);
                    server.getPluginManager().callEvent(telnetEvent);

                    if (telnetEvent.isCancelled())
                    {
                        return;
                    }

                    // Deprecated
                    final RemoteServerCommandEvent serverEvent = new RemoteServerCommandEvent(telnetEvent.getSender(), telnetEvent.getCommand());
                    server.getPluginManager().callEvent(serverEvent);

                    final String command = serverEvent.getCommand();
                    if (command != null && !command.isEmpty())
                    {
                        server.dispatchCommand(sender, command);
                    }
                }
            }.runTask(BukkitTelnet.getPlugin());
        }
        catch (Exception ex)
        {
            BT_Log.severe(ex);
        }
    }

    public CommandSender getCommandSender()
    {
        if (this.sessionCommandSender == null)
        {
            this.sessionCommandSender = new SessionCommandSender(this);
        }
        return this.sessionCommandSender;
    }

    @Override
    public void run()
    {
        try
        {
            synchronized (this.clientSocket)
            {
                this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                this.writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            }
        }
        catch (IOException ex)
        {
            BT_Log.severe(ex);
        }

        if (this.reader == null || this.writer == null)
        {
            terminateSession();
            return;
        }

        writeOut(":");

        writeOutFormatted("Session Started.", true);

        if (authenticateSession())
        {
            sessionMainLoop();
        }
        else
        {
            writeOutFormatted("Authentication failed.", true);
        }

        terminateSession();
    }

    private boolean authenticateSession()
    {
        if (this.hasTerminated)
        {
            return false;
        }

        try
        {
            boolean isPreAuthenticated = false;

            if (this.clientAddress != null)
            {
                final Iterator<Map.Entry<String, List<String>>> it = BT_Config.getInstance().getConfigEntries().getAdmins().entrySet().iterator();
                adminMapLoop:
                while (it.hasNext())
                {
                    final Map.Entry<String, List<String>> entry = it.next();
                    final List<String> adminIPs = entry.getValue();
                    if (adminIPs != null)
                    {
                        for (final String ip : adminIPs)
                        {
                            if (fuzzyIpMatch(ip, this.clientAddress, 3))
                            {
                                isPreAuthenticated = true;
                                this.userName = entry.getKey();
                                break adminMapLoop;
                            }
                        }
                    }
                }
            }

            final TelnetPreLoginEvent event = new TelnetPreLoginEvent(this.clientAddress, isPreAuthenticated ? this.userName : null, isPreAuthenticated);

            Bukkit.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled())
            {
                return false;
            }

            if (event.canBypassPassword())
            {
                this.userName = event.getName();
                return true;
            }
            else
            {
                boolean gotValidUsername = false;
                int tries = 0;
                while (tries++ < 3)
                {
                    try
                    {
                        writeOutFormatted("Username: ", false);

                        String _userName = reader.readLine();
                        writeOut(":");

                        if (_userName != null && !_userName.isEmpty())
                        {
                            _userName = AUTH_INPUT_FILTER.matcher(_userName).replaceAll("").trim();
                        }

                        if (_userName != null && !_userName.isEmpty())
                        {
                            this.userName = _userName;
                            gotValidUsername = true;
                            break;
                        }
                        else
                        {
                            writeOutFormatted("Invalid username.", true);
                        }
                    }
                    catch (IOException ex)
                    {
                    }
                }

                if (!gotValidUsername)
                {
                    return false;
                }

                tries = 0;
                while (tries++ < 3)
                {
                    try
                    {
                        writeOutFormatted("Password: ", false);

                        String _password = reader.readLine();
                        writeOut(":");

                        if (_password != null && !_password.isEmpty())
                        {
                            _password = AUTH_INPUT_FILTER.matcher(_password).replaceAll("").trim();
                        }

                        if (_password != null && !_password.isEmpty() && BT_TelnetServer.getInstance().getPassword().equals(_password))
                        {
                            return true;
                        }
                        else
                        {
                            writeOutFormatted("Invalid password.", true);
                            Thread.sleep(2000);
                        }
                    }
                    catch (IOException ex)
                    {
                    }
                }
            }
        }
        catch (Exception ex)
        {
            BT_Log.severe(ex);
        }

        return false;
    }

    private void sessionMainLoop()
    {
        if (this.hasTerminated)
        {
            return;
        }

        try
        {
            writeOutFormatted("Logged in as " + this.userName + ".", true);
            BT_Log.info(this.clientAddress + " logged in as \"" + this.userName + "\".");

            BT_Log.getLogger().addHandler(this.sessionLogHandler = new SessionLogHandler(this));

            while (this.isConnected())
            {
                String command = null;
                try
                {
                    command = this.reader.readLine();
                }
                catch (IOException ex)
                {
                }

                writeOut(":");

                if (command != null)
                {
                    command = COMMAND_INPUT_FILTER.matcher(NONASCII_FILTER.matcher(command).replaceAll("")).replaceFirst("").trim();
                    if (!command.isEmpty())
                    {
                        if (command.toLowerCase().startsWith("telnet"))
                        {
                            if (command.equalsIgnoreCase("telnet.help"))
                            {
                                writeOut("Telnet commands:\r\n");
                                writeOut("telnet.help - See all of the telnet commands.\r\n");
                                writeOut("telnet.stopserver - Shutdown the server.\r\n");
                                writeOut("telnet.log - Change your logging settings.\r\n");
                            }
                            else if (command.equalsIgnoreCase("telnet.stopserver"))
                            {
                                writeOut("Shutting down the server...\r\n");
                                System.exit(0);
                            }
                            else if (command.equalsIgnoreCase("telnet.log"))
                            {
                                if (filter_mode == FilterMode.CHAT_ONLY)
                                {
                                    filter_mode = FilterMode.FULL;
                                    writeOut("Showing full console log.\r\n");
                                }
                                else
                                {
                                    filter_mode = FilterMode.CHAT_ONLY;
                                    writeOut("Showing chat log only.\r\n");
                                }
                            }
                        }
                        else
                        {
                            sendBukkitCommand(command);
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            BT_Log.severe(ex);
        }
    }

    private static boolean fuzzyIpMatch(String a, String b, int octets)
    {
        boolean match = true;

        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");

        if (aParts.length != 4 || bParts.length != 4)
        {
            return false;
        }

        if (octets > 4)
        {
            octets = 4;
        }
        else if (octets < 1)
        {
            octets = 1;
        }

        for (int i = 0; i < octets && i < 4; i++)
        {
            if (aParts[i].equals("*") || bParts[i].equals("*"))
            {
                continue;
            }

            if (!aParts[i].equals(bParts[i]))
            {
                match = false;
                break;
            }
        }

        return match;
    }

    private static class SessionLogHandler extends BT_Handler
    {
        private final BT_ClientSession session;

        public SessionLogHandler(BT_ClientSession session)
        {
            this.session = session;
        }

        @Override
        public void writeOut(String string)
        {
            this.session.writeOut(string);
        }

        @Override
        public void flush()
        {
            this.session.flush();
        }

        @Override
        public void close() throws SecurityException
        {
            this.session.terminateSession();
        }
    }

    private static class SessionCommandSender extends BT_CommandSender
    {
        private final BT_ClientSession session;

        public SessionCommandSender(BT_ClientSession session)
        {
            this.session = session;
        }

        @Override
        public void sendMessage(String message)
        {
            if (this.session.sessionLogHandler != null)
            {
                this.session.sessionLogHandler.writeOut(message + "\r\n:");
            }
        }

        @Override
        public String getName()
        {
            return this.session.getUserName();
        }
    }
}
