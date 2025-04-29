public class TemporalState {
    private final long timestamp;

    public TemporalState(long time) {
        this.timestamp = time;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void restoreState(GamePanel panel) {
        System.err.println("TemporalState.restoreState() not implemented");
    }

    @Override
    public String toString() {
        return "TemporalState{timestamp=" + timestamp + "}";
    }
}
