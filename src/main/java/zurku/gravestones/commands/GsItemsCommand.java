package zurku.gravestones.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import zurku.gravestones.GravestoneSettings;
import javax.annotation.Nonnull;

public class GsItemsCommand extends CommandBase {

    private final GravestoneSettings settings;

    public GsItemsCommand(GravestoneSettings settings) {
        super("gsitems", "Toggle gravestone item access (instant/gui)");
        setPermissionGroups(new String[]{"OP"});
        this.settings = settings;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        settings.toggleInstantCollect();
        String mode = settings.isInstantCollect() ? "Instant" : "Gui";
        ctx.sendMessage(Message.raw("Gravestone Zugriff " + mode));
    }
}
