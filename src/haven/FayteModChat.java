package haven;

import java.util.List;
import java.util.Map;

public class FayteModChat extends FayteModule {
    private GameUI restored = null;

    public FayteModChat() {
        super(
                "chat_windows",
                "Chat Windows",
                "Movable, resizable chat windows with your own choice of channels.",
                FayteConfig.moduleChat);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("chatwindow", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteChatWindow.create(gui());
            }
        });
        cmds.put("classicchat", new Console.Command() {
            public void run(Console cons, String[] args) {
                boolean on = !FayteConfig.classicChat.get();
                FayteConfig.classicChat.set(on);
                FayteMsg.say(
                        on ? "Classic chat shown" : "Classic chat hidden; press Enter to type in your Chat Window");
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("chatwindow");
        cmds.add("classicchat");
    }

    @Override
    public void tick(GameUI gui) {
        if (restored != gui) {
            restored = gui;
            FayteChatWindow.restore(gui);
        }
    }
}
