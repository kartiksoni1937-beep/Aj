package hack.echo.backend.command;

import hack.echo.backend.command.impl.*;
import hack.echo.backend.Backend;

import lombok.Getter;

import java.util.ArrayList;

@Getter
public class CommandManager {

    private final ArrayList<Command> commands = new ArrayList<>();

    public void init() {
        commands.add(new Ban());
        commands.add(new Broadcast());
        commands.add(new Help());
        commands.add(new Kick());
        commands.add(new List());
        commands.add(new Lookup());
        commands.add(new MigrateFlexible());
        commands.add(new PauseSub());
        commands.add(new Register());
        commands.add(new ResetHWID());
        commands.add(new ResetVL());
        commands.add(new ResumeSub());
        commands.add(new SetBeta());
        commands.add(new SetHWID());
        commands.add(new SetMaintenance());
        commands.add(new Stop());
        commands.add(new Unban());
        commands.add(new Version());
    }

    public void runCommand(String[] args) {
        if (args.length == 0) {
            return;
        }
        Command command = getCommand(args[0]);
        String[] newArgs = new String[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, args.length - 1);
        if (command != null) {
            command.execute(newArgs);
        } else {
            Backend.logger.error("Command not found");
        }
    }

    public Command getCommand(String name) {
        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(name)) {
                return command;
            }
        }
        return null;
    }
}
