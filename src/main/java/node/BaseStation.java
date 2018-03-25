package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.ActivationTask;
import task.FinishSimulationTask;
import task.LeaderSelectionTask;

import java.util.HashMap;
import java.util.Random;

public class BaseStation extends Process {

    private static final double COMPUTE_SIZE = 0.0d;
    private static final double COMMUNICATION_SIZE = 0.0d;

    private static final int MAX_RANK = 1000;

    public BaseStation(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 2)
            Msg.info("Wrong number of arguments for BaseStation node");
        int id = Integer.parseInt(args[0]);
        int nodeCount = Integer.parseInt(args[1]);
        System.out.println("BaseStation NODE " + id + " STARTED");

        HashMap<String, Integer> ranks = generateRandomRanks(nodeCount);

        //Wait for other nodes to start
        try {
            waitFor(3000);
        } catch (HostFailureException e) {
            System.err.println("BaseStation host failed!!");
            return;
        }

        //Send activation task
        for (int i = 0; i < nodeCount; i++) {
            ActivationTask activationTask = new ActivationTask("ACTIVATION_TASK_" + i, COMPUTE_SIZE, COMMUNICATION_SIZE, ranks);

            try {
                activationTask.send("node_" + i);
            } catch (TransferFailureException e) {
                e.printStackTrace();
            } catch (HostFailureException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }

        //Wait for everyone to do the necessary configurations
        try {
            waitFor(5000);
        } catch (HostFailureException e) {
            System.err.println("BaseStation host failed!!");
            return;
        }

        //Trigger initial leader selection
        for (int i = 0; i < nodeCount; i++) {
            LeaderSelectionTask activationTask = new LeaderSelectionTask();

            try {
                activationTask.send("node_" + i);
            } catch (TransferFailureException e) {
                e.printStackTrace();
            } catch (HostFailureException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }

        //Wait for leader to be selected
        try {
            waitFor(10000);
        } catch (HostFailureException e) {
            System.err.println("BaseStation host failed!!");
            return;
        }

        //Send ending task to everyone
        for (int i = 0; i < nodeCount; i++) {
            FinishSimulationTask finishTask = new FinishSimulationTask();

            try {
                finishTask.send("node_" + i);
            } catch (TransferFailureException e) {
                e.printStackTrace();
            } catch (HostFailureException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }

        System.out.println("BASE STATION FINISHED EXECUTION");
    }

    private HashMap<String, Integer> generateRandomRanks(int nodeCount) {
        HashMap<String, Integer> ranks = new HashMap<>();
        Random generator = new Random();

        for (int i = 0; i < nodeCount; i++)
            ranks.put("node_" + i, generator.nextInt(MAX_RANK));

        return ranks;
    }
}