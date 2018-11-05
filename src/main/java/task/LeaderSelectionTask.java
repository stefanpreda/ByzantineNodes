package task;

import org.simgrid.msg.Task;

public class LeaderSelectionTask extends Task {

    private String originHost;
    private String destinationHost;

    public LeaderSelectionTask() {}

    public LeaderSelectionTask(String originHost, String destinationHost) {
        this.originHost = originHost;
        this.destinationHost = destinationHost;
    }

    public String getOriginHost() {
        return originHost;
    }

    public void setOriginHost(String originHost) {
        this.originHost = originHost;
    }

    public String getDestinationHost() {
        return destinationHost;
    }

    public void setDestinationHost(String destinationHost) {
        this.destinationHost = destinationHost;
    }
}
