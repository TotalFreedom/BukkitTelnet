package me.StevenLawson.BukkitTelnet;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.bukkit.ChatColor;

public abstract class BT_Handler extends Handler
{
    private Formatter formatter;

    public abstract void writeOut(String string);

    @Override
    public final void publish(LogRecord record)
    {
        if (record == null)
        {
            return;
        }

        String message = record.getMessage();

        if (this.formatter == null)
        {
            this.formatter = getFormatter();
        }

        if (this.formatter != null)
        {
            message = this.formatter.formatMessage(record);
        }

        message = ChatColor.stripColor(message);

        if (filter_mode == FilterMode.CHAT_ONLY)
        {
            if (message.startsWith("<") || message.startsWith("[Server:") || message.startsWith("[CONSOLE]<") || message.startsWith("[ADMIN]")))
            {
                writeOut(message + "\r\n:");
            }
        }
        else if (filter_mode == FilterMode.NONCHAT_ONLY)
        {
            if (!(message.startsWith("<") || message.startsWith("[Server:") || message.startsWith("[CONSOLE]<") || message.startsWith("[ADMIN]")))
            {
                writeOut(message + "\r\n:");
            }
        }
        else
        {
            writeOut(message + "\r\n:");
        }
    }

    @Override
    public final Formatter getFormatter()
    {
        if (this.formatter == null)
        {
            this.formatter = super.getFormatter();

            Handler[] handlers = org.bukkit.Bukkit.getLogger().getHandlers();
            for (Handler handler : handlers)
            {
                if (handler.getClass().getName().startsWith("org.bukkit.craftbukkit"))
                {
                    Formatter _formatter = handler.getFormatter();
                    if (_formatter != null)
                    {
                        this.formatter = _formatter;
                        break;
                    }
                }
            }
        }
        return this.formatter;
    }
}
