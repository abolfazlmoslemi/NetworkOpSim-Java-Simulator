import java.awt.Point;
import java.util.Objects;

public class Wire {
    private static int nextId = 0;
    private final int id;
    private final Port startPort;
    private final Port endPort;
    private final double length;

    public Wire(Port startPort, Port endPort) {
        this.id = nextId++;
        this.startPort = Objects.requireNonNull(startPort);
        this.endPort   = Objects.requireNonNull(endPort);
        Point p1 = startPort.getPosition();
        Point p2 = endPort.getPosition();
        this.length = p1.distance(p2);
    }

    public int getId() {
        return id;
    }

    public Port getStartPort() {
        return startPort;
    }

    public Port getEndPort() {
        return endPort;
    }

    public double getLength() {
        return length;
    }

    public void destroy() {
        System.out.println("Destroying wire " + id);
    }

    @Override
    public String toString() {
        return "Wire{" +
                "id=" + id +
                ", start=" + startPort.getId() +
                ", end=" + endPort.getId() +
                ", length=" + length +
                '}';
    }
}
