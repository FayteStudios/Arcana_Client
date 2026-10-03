package haven;

public class FayteSession {
    private static GameUI current = null;

    public static void tick(GameUI gui) {
        if (gui == current) {
            return;
        }
        current = gui;
        if (Config.currentCharName == null || Config.currentCharName.isEmpty()) {
            FayteMsg.say(
                    "New character: you start from the Default layout. The game doesn't tell Arcana a new character's"
                            + " name until you log in again, so changes are saved from your next login.",
                    GameUI.MsgType.INFO);
        }
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
    }
}
