import java.util.*;
public class GraphUtils {
    private GraphUtils() {}
    public static boolean isNetworkConnected(List<System> systems, List<Wire> wires) {
        if (systems == null || systems.isEmpty() || systems.size() == 1) {
            return true; 
        }
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (System s : systems) {
            adj.put(s.getId(), new HashSet<>()); 
        }
        for (Wire w : wires) {
            if (w != null && w.getStartPort() != null && w.getEndPort() != null &&
                    w.getStartPort().getParentSystem() != null && w.getEndPort().getParentSystem() != null)
            {
                System sys1 = w.getStartPort().getParentSystem();
                System sys2 = w.getEndPort().getParentSystem();
                adj.get(sys1.getId()).add(sys2.getId());
                adj.get(sys2.getId()).add(sys1.getId());
            }
        }
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        System startSystem = systems.get(0); 
        queue.offer(startSystem.getId());
        visited.add(startSystem.getId());
        while (!queue.isEmpty()) {
            int currentSysId = queue.poll();
            Set<Integer> neighbors = adj.get(currentSysId);
            if (neighbors != null) {
                for (int neighborId : neighbors) {
                    if (!visited.contains(neighborId)) {
                        visited.add(neighborId);
                        queue.offer(neighborId);
                    }
                }
            }
        }
        return visited.size() == systems.size();
    }
    public static boolean areEssentialPortsConnected(List<System> systems) {
        if (systems == null) return true; 
        for (System s : systems) {
            if (s == null) continue;
            if (s.isReferenceSystem()) {
                if (s.hasOutputPorts()) { 
                    for (Port p : s.getOutputPorts()) {
                        if (!p.isConnected()) {
                            java.lang.System.out.println("Validation Fail: Source System " + s.getId() + " Output Port " + p.getId() + " ("+p.getShape()+") is not connected.");
                            return false; 
                        }
                    }
                } else if (s.hasInputPorts()) { 
                    for (Port p : s.getInputPorts()) {
                        if (!p.isConnected()) {
                            java.lang.System.out.println("Validation Fail: Sink System " + s.getId() + " Input Port " + p.getId() + " ("+p.getShape()+") is not connected.");
                            return false; 
                        }
                    }
                }
            }
        }
        return true; 
    }
}