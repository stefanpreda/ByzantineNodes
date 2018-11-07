package task;

import org.simgrid.msg.Task;

public class DataAckTask extends Task{

    private float result;

    private String leader;
    private String originHost;
    private String destinationHost;

    public String getOriginHost() {
        return originHost;
    }

    public void setOriginHost(String originHost) {
        this.originHost = originHost;
    }

    public float getResult() {
        return result;
    }

    public void setResult(float result) {
        this.result = result;
    }

    public String getDestinationHost() {
        return destinationHost;
    }

    public void setDestinationHost(String destinationHost) {
        this.destinationHost = destinationHost;
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }
}
