package task;

import org.simgrid.msg.Task;

public class SimpleTask extends Task{

    private String originHost;

    private String destinationHost;

    public SimpleTask() {
        super();
    }

    public SimpleTask(String originHost, String destinationHost) {
        super();
        this.originHost = originHost;
        this.destinationHost = destinationHost;
    }

    public SimpleTask(String name, double computeSize, double communicationSize, String originHost, String destinationHost) {
        super(name, computeSize, communicationSize);
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
