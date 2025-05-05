public class TemporalState {
    private final long timestamp; 
    public TemporalState(long time) {
        this.timestamp = time;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void restoreState(GamePanel panel) {
        java.lang.System.err.println("TemporalState.restoreState() - Functionality is NOT IMPLEMENTED.");
    }
    @Override
    public String toString() {
        return "TemporalState{timestamp=" + timestamp + " [NOT IMPLEMENTED]}";
    }
}