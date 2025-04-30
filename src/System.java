import java.util.Queue;
import java.util.LinkedList;

public class System {
    private static int nextId = 0;
    private final int id;
    private final boolean isReferenceSystem;
    private final Queue<Packet> packetQueue = new LinkedList<>();

    public System(boolean isReferenceSystem) {
        this.id = nextId++;
        this.isReferenceSystem = isReferenceSystem;
    }

    public int getId() {
        return id;
    }

    public void receivePacket(Packet packet, GamePanel panel) {
        if (isReferenceSystem) {
            panel.packetSuccessfullyDelivered(packet);
        } else {
            if (packetQueue.size() < 5) {
                packetQueue.offer(packet);
            } else {
                panel.packetLost(packet);
            }
        }
    }

    public void processQueue(GamePanel panel) {
        if (!packetQueue.isEmpty()) {
            Packet next = packetQueue.peek();
            
        }
    }
}
