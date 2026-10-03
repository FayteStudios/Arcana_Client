package haven;

import java.awt.event.KeyEvent;

public class FayteNameWnd extends Window {
    private final String key;

    public FayteNameWnd(GameUI gui, String key, String what, String cur) {
        super(Coord.z, new Coord(FayteSkin.s(260), FayteSkin.s(50)), gui, "Name " + what);
        this.key = key;
        justclose = true;
        new Label(new Coord(0, 0), this, "Enter to save, empty to clear, Esc to cancel");
        TextEntry e = new TextEntry(new Coord(0, FayteSkin.s(20)), FayteSkin.s(260), this, cur == null ? "" : cur) {
            @Override
            public void activate(String text) {
                FayteLabels.set(FayteNameWnd.this.key, text);
                FayteNameWnd.this.ui.destroy(FayteNameWnd.this);
            }

            @Override
            public boolean type(char c, KeyEvent ev) {
                if (c == 27) {
                    FayteNameWnd.this.ui.destroy(FayteNameWnd.this);
                    return true;
                }
                return super.type(c, ev);
            }
        };
        pack();
        c = gui.sz.sub(sz).div(2);
        setfocus(e);
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
