package task;

import java.util.HashMap;

public class ActivationTask extends SimpleTask{

    private HashMap<String, Integer> ranks;

    public ActivationTask() {
        ranks = new HashMap<>();
    }

    public ActivationTask(String name, double computeSize, double communicationSize, HashMap<String, Integer> ranks,
                          String originHost, String destinationHost) {
        super(name, computeSize, communicationSize, originHost, destinationHost);
        this.ranks = ranks;
    }

    public HashMap<String, Integer> getRanks() {
        return ranks;
    }

    public void setRanks(HashMap<String, Integer> ranks) {
        this.ranks = ranks;
    }
}
