package task;

import org.simgrid.msg.Task;

import java.util.LinkedList;
import java.util.Queue;

public class MultiDestinationTask extends Task{
    private Queue<String> destinationOrder;
    private String finalDestination;

    public MultiDestinationTask() {
        destinationOrder = new LinkedList<String>();
    }

    public MultiDestinationTask(String name, double computeSize, double communicationSize) {
        super(name, computeSize, communicationSize);
        destinationOrder = new LinkedList<String>();
    }

    public void addNextDestination(String nextDestination) {
        destinationOrder.add(nextDestination);
    }

    public String getNextDestination() {

        if (destinationOrder.isEmpty())
            return null;

        return destinationOrder.poll();
    }

    public String getFinalDestination() {
        return finalDestination;
    }

    public void setFinalDestination(String finalDestination) {
        this.finalDestination = finalDestination;
    }
}
