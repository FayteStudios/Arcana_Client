package haven;

public class FayteOpt {
    public static boolean shots() {
        return Utils.getprefb("fayte_printscreen", true);
    }

    public static void setshots(boolean v) {
        Utils.setprefb("fayte_printscreen", v);
    }
}
