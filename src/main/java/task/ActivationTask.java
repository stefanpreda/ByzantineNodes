package task;

import org.simgrid.msg.Task;

import java.util.HashMap;

public class ActivationTask extends Task{

    private HashMap<String, Integer> ranks;
    private String originHost;

    private String destinationHost;

    public ActivationTask() {
        ranks = new HashMap<>();
    }

    public ActivationTask(String name, double computeSize, double communicationSize, HashMap<String, Integer> ranks,
                          String originHost, String destinationHost) {
        super(name, computeSize, communicationSize);
        this.ranks = ranks;
        this.originHost = originHost;
        this.destinationHost = destinationHost;
    }

    public HashMap<String, Integer> getRanks() {
        return ranks;
    }

    public void setRanks(HashMap<String, Integer> ranks) {
        this.ranks = ranks;
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
