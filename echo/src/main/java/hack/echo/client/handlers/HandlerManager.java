package hack.echo.client.handlers;

import hack.echo.client.event.EventManager;
import hack.echo.client.handlers.impl.*;

import java.util.ArrayList;

public class HandlerManager {
    private final ArrayList<Handler> handlers = new ArrayList<>();

    public void initialize() {
        handlers.add(new KeyHandler());
        handlers.add(new HurtTickHandler());
        handlers.add(new SprintController());
        handlers.add(new SwapStateManager());
        handlers.add(new CommandHandler());
        handlers.add(new BroadcastHandler());

        for (Handler handler : handlers) {
            EventManager.register(handler);
        }
    }

    public void shutdown() {
        for (Handler handler : handlers) {
            EventManager.unregister(handler);
        }
        SwapStateManager.clear();
        handlers.clear();
    }
}