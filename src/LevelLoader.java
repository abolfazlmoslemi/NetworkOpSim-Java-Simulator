import java.util.ArrayList;
import java.util.List;

public class LevelLoader {
    public static class LevelLayout {
        public final List<System> systems;
        public final List<Wire>   wires;
        public LevelLayout(List<System> systems, List<Wire> wires) {
            this.systems = systems;
            this.wires   = wires;
        }
    }

    public static LevelLayout loadLevel(int level) {
        List<System> systems = new ArrayList<>();
        List<Wire>   wires   = new ArrayList<>();

        switch (level) {
            case 1:
                System src  = new System(true);
                System node = new System(false);
                System sink = new System(true);
                systems.add(src);
                systems.add(node);
                systems.add(sink);
                break;
            // future cases...
        }

        return new LevelLayout(systems, wires);
    }
}
