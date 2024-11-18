package lol.petrik.pmcauth.limbo;
import java.util.Collection;
import java.util.Collections;

import ua.nanit.limbo.server.CommandHandler;
import ua.nanit.limbo.server.Command;


public class CustomCommandHandler implements CommandHandler<Command> {
  @Override
  public Collection<Command> getCommands() {
    return Collections.emptyList();
  }

  public void register(Command command) {
  }

  public boolean executeCommand(String input) {
    return true;
  }
}