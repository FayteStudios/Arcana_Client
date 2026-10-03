package haven;

public class FayteEdges {
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int TOP = 4;
    public static final int BOTTOM = 8;

    public static int at(Coord c, Coord sz, int m) {
        int e = 0;
        if (c.x >= 0 && c.x < m) {
            e |= LEFT;
        }
        if (c.x < sz.x && c.x >= sz.x - m) {
            e |= RIGHT;
        }
        if (c.y >= 0 && c.y < m) {
            e |= TOP;
        }
        if (c.y < sz.y && c.y >= sz.y - m) {
            e |= BOTTOM;
        }
        return e;
    }

    public static Coord[] apply(int e, Coord c0, Coord sz0, Coord d, Coord min) {
        int x = c0.x, y = c0.y, w = sz0.x, h = sz0.y;
        if ((e & RIGHT) != 0) {
            w = Math.max(min.x, sz0.x + d.x);
        }
        if ((e & BOTTOM) != 0) {
            h = Math.max(min.y, sz0.y + d.y);
        }
        if ((e & LEFT) != 0) {
            w = Math.max(min.x, sz0.x - d.x);
            x = c0.x + sz0.x - w;
        }
        if ((e & TOP) != 0) {
            h = Math.max(min.y, sz0.y - d.y);
            y = c0.y + sz0.y - h;
        }
        return new Coord[] {new Coord(x, y), new Coord(w, h)};
    }

    public static String tip(int e) {
        return e == 0 ? null : "Drag to resize";
    }
}
