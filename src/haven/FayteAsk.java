package haven;

import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class FayteAsk extends Window {
    public FayteAsk(GameUI gui, String title, String hint, String cur, Consumer<String> done) {
        super(Coord.z, new Coord(FayteSkin.s(300), FayteSkin.s(50)), gui, title);
        justclose = true;
        new Label(new Coord(0, 0), this, hint);
        TextEntry e = new TextEntry(new Coord(0, FayteSkin.s(20)), FayteSkin.s(300), this, cur == null ? "" : cur) {
            @Override
            public void activate(String text) {
                FayteAsk.this.ui.destroy(FayteAsk.this);
                done.accept(text.trim());
            }

            @Override
            public boolean type(char c, KeyEvent ev) {
                if (c == 27) {
                    FayteAsk.this.ui.destroy(FayteAsk.this);
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
