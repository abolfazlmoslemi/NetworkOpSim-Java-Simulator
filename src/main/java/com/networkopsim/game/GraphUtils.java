package com.networkopsim.game;
import java.util.*;

public class GraphUtils {

    private GraphUtils() {} // Private constructor to prevent instantiation

    /**
     * Checks if the network of systems is connected (all systems are reachable from each other).
     * Uses Breadth-First Search (BFS).
     *
     * @param systems List of all systems in the network.
     * @param wires List of all wires connecting the systems.
     * @return true if the network is connected or empty/single node, false otherwise.
     */
    public static boolean isNetworkConnected(List<System> systems, List<Wire> wires) {
        if (systems == null || systems.isEmpty() || systems.size() == 1) {
            return true; // An empty or single-node graph is considered connected.
        }

        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (System s : systems) {
            if (s != null) { // Ensure system is not null
                adj.put(s.getId(), new HashSet<>());
            }
        }

        for (Wire w : wires) {
            if (w != null && w.getStartPort() != null && w.getEndPort() != null &&
                    w.getStartPort().getParentSystem() != null && w.getEndPort().getParentSystem() != null)
            {
                System sys1 = w.getStartPort().getParentSystem();
                System sys2 = w.getEndPort().getParentSystem();
                // Ensure systems exist in the adjacency list before trying to add edges
                if (adj.containsKey(sys1.getId()) && adj.containsKey(sys2.getId())) {
                    adj.get(sys1.getId()).add(sys2.getId());
                    adj.get(sys2.getId()).add(sys1.getId());
                }
            }
        }

        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();

        // Find the first non-null system to start BFS
        System startSystem = null;
        for (System s : systems) {
            if (s != null) {
                startSystem = s;
                break;
            }
        }
        if (startSystem == null) return true; // All systems are null, trivially connected/empty.

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
        // Count non-null systems to compare with visited set size
        long nonNullSystemCount = systems.stream().filter(Objects::nonNull).count();
        return visited.size() == nonNullSystemCount;
    }

    /**
     * Checks if all ports on ALL systems (reference and non-reference) are connected.
     *
     * @param systems List of all systems in the network.
     * @return true if all ports of all systems are connected, false otherwise.
     */
    public static boolean areAllSystemPortsConnected(List<System> systems) {
        if (systems == null) return true; // Or false, depending on desired behavior for null list

        for (System s : systems) {
            if (s == null) continue; // Skip null systems if any

            // Check all input ports
            for (Port p : s.getInputPorts()) {
                if (p != null && !p.isConnected()) {
                    java.lang.System.out.println("Validation Fail: System " + s.getId() + " Input Port " + p.getId() + " ("+p.getShape()+") is not connected.");
                    return false; // Found an unconnected input port
                }
            }

            // Check all output ports
            for (Port p : s.getOutputPorts()) {
                if (p != null && !p.isConnected()) {
                    java.lang.System.out.println("Validation Fail: System " + s.getId() + " Output Port " + p.getId() + " ("+p.getShape()+") is not connected.");
                    return false; // Found an unconnected output port
                }
            }
        }
        return true; // All ports on all systems are connected
    }
}