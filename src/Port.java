import java.awt.Point;
import java.util.Objects;

public class Port {
    private static int nextId = 0;
    private final int id;
    private final boolean isOutput;
    private Point position;

    public Port(int x, int y, boolean isOutput) {
        this.id = nextId++;
        this.position = new Point(x, y);
        this.isOutput = isOutput;
    }

    public int getId() {
        return id;
    }

    public boolean isOutput() {
        return isOutput;
    }

    public Point getPosition() {
        return position;
    }

    public void setPosition(int x, int y) {
        this.position = new Point(x, y);
    }

    @Override
    public String toString() {
        return "Port{" +
                "id=" + id +
                ", isOutput=" + isOutput +
                ", position=" + position +
                '}';
    }
}
