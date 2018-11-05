package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.ActivationTask;
import task.FinalDataResultTask;
import task.FinishSimulationTask;
import task.LeaderSelectionTask;
import task.TurnOffRequest;

import java.util.HashMap;
import java.util.Random;

public class BaseStation extends Process {

    private static final double COMPUTE_SIZE = 0.0d;
    private static final double COMMUNICATION_SIZE = 0.0d;

    private static final int MAX_RANK = 1000;

    private static final double RECEIVE_TIMEOUT = 0.8;

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final double SIMULATION_TIMEOUT = 500000;

    //The time when base station started listening for dispute messages
    private long disputeWaitStartTime = -1;

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final long DISPUTE_TIMEOUT = 25000;



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

        System.out.println(ranks);

        //Wait for other nodes to start
        try {
            waitFor(3000);
        } catch (HostFailureException e) {
            System.err.println("BaseStation host failed!!");
            return;
        }

        //Send activation task
        for (int i = 0; i < nodeCount; i++) {
            ActivationTask activationTask = new ActivationTask("ACTIVATION_TASK_" + i, COMPUTE_SIZE, COMMUNICATION_SIZE, ranks,
                    "node_" + id, "node_" + i);

            try {
                activationTask.send("node_" + i);
            } catch (TransferFailureException | HostFailureException | TimeoutException e) {
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
            LeaderSelectionTask leaderSelectionTask = new LeaderSelectionTask("node_" + id, "node_" + id);

            try {
                leaderSelectionTask.send("node_" + i);
            } catch (TransferFailureException | TimeoutException | HostFailureException e) {
                e.printStackTrace();
            }
        }

        long startTime = System.currentTimeMillis();

        while (true) {

            Task task = null;

            //RECEIVE LOOP
            try {
                task = Task.receive(Host.currentHost().getName(), RECEIVE_TIMEOUT);
            } catch (TransferFailureException | HostFailureException e) {
                System.err.println("Node " + id + " FAILED to transfer data\n" + e.getLocalizedMessage());
            } catch (TimeoutException ignored) {

            }

            if (System.currentTimeMillis() - startTime >= SIMULATION_TIMEOUT)
                break;

            if (task != null && task instanceof TurnOffRequest) {
                TurnOffRequest turnOffRequest = (TurnOffRequest)task;

                try {
                    Host.getByName(turnOffRequest.getName()).off();
                } catch (HostNotFoundException e) {
                    e.printStackTrace();
                }

            }

            if (task != null && task instanceof FinalDataResultTask) {
                FinalDataResultTask finalDataResultTask = (FinalDataResultTask)task;

                System.out.println("BaseStation NODE " + id + " RECEIVED FINAL MEASUREMENT RESULT: "
                    + finalDataResultTask.getResult() + " FROM NODE " + finalDataResultTask.getOriginHost());

                //#TODO Send ack message to all
                //#TODO Wait for leader/measurement dispute messages
                //#TODO Discard measurement if > threshold + timeout sender, send update leader to wrong nodes if < threashold
                //#TODO Discard measurement + elect new leader if > threshold, send updated measurement to wrong nodes if < threshold

                System.out.println("BaseStation NODE " + id + " ACCEPTED FINAL MEASUREMENT RESULT: "
                    + finalDataResultTask.getResult() + " FROM NODE " + finalDataResultTask.getOriginHost());
            }
        }

        //Send ending task to everyone
        for (int i = 0; i < nodeCount; i++) {
            FinishSimulationTask finishTask = new FinishSimulationTask();

            try {
                finishTask.send("node_" + i);
            } catch (TransferFailureException | HostFailureException | TimeoutException e) {
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