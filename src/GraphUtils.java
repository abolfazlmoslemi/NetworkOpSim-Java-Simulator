import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class GraphUtils {
    public static boolean isNetworkConnected(List<System> systems, List<Wire> wires) {
        if (systems.size() < 2) return true;
        Set<System> visited = new HashSet<>();
        Queue<System> queue = new LinkedList<>();
        queue.add(systems.get(0));
        visited.add(systems.get(0));

        while (!queue.isEmpty()) {
            System cur = queue.poll();
            for (Wire w : wires) {
                System other = null;
                if (w.getStartPort().getParentSystem() == cur)
                    other = w.getEndPort().getParentSystem();
                else if (w.getEndPort().getParentSystem() == cur)
                    other = w.getStartPort().getParentSystem();

                if (other != null && visited.add(other))
                    queue.add(other);
            }
        }

        return visited.size() == systems.size();
    }
}
