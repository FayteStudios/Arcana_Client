package haven;

public class FayteSession {
    private static GameUI current = null;

    public static void tick(GameUI gui) {
        if (gui == current) {
            return;
        }
        current = gui;
        FayteNotesPanel.save(true);
        FayteRecipes.reset();
        FayteBagSel.reset();
        FayteMacros.stop();
        FayteAchieveToast.reset();
        FayteAbacus.reset();
        FayteChatWindow.reset();
        FayteKinReq.reset();
        FayteKeys.reset();
        FayteSkillGoal.reset();
        FaytePilgrims.flush();
        if (Config.currentCharName == null || Config.currentCharName.isEmpty()) {
            FayteMsg.say(
                    "New characters start with the default layout and journal. Enjoy the Game! -Sorin",
                    GameUI.MsgType.INFO);
        }
    }
}
