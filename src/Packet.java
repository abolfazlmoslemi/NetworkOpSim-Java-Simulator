import java.awt.Point;
import java.util.Objects;

public class Packet {
    private static int nextId = 0;
    private final int id;
    private final PacketShape shape;
    private final int size;
    private Point position;

    public Packet(PacketShape shape, int startX, int startY) {
        this.id = nextId++;
        this.shape = Objects.requireNonNull(shape);
        this.position = new Point(startX, startY);

        switch (shape) {
            case SQUARE:
                size = 2;
                break;
            case TRIANGLE:
                size = 3;
                break;
            default:
                throw new IllegalArgumentException("Unsupported shape: " + shape);
        }
    }

    public int getId() {
        return id;
    }

    public PacketShape getShape() {
        return shape;
    }

    public int getSize() {
        return size;
    }

    public Point getPosition() {
        return position;
    }

    public void setPosition(int x, int y) {
        this.position = new Point(x, y);
    }

    @Override
    public String toString() {
        return "Packet{" +
                "id=" + id +
                ", shape=" + shape +
                ", size=" + size +
                ", position=" + position +
                '}';
    }
}
