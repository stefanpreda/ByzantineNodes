package task;

import org.simgrid.msg.Task;

import java.util.HashMap;

public class ActivationTask extends Task{

    private HashMap<String, Integer> ranks;

    public ActivationTask() {
        ranks = new HashMap<>();
    }

    public ActivationTask(String name, double computeSize, double communicationSize, HashMap<String, Integer> ranks) {
        super(name, computeSize, communicationSize);
        this.ranks = ranks;
    }

    public HashMap<String, Integer> getRanks() {
        return ranks;
    }

    public void setRanks(HashMap<String, Integer> ranks) {
        this.ranks = ranks;
    }
}
