package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.ActivationTask;
import task.DataAckTask;
import task.DataDisputeTask;
import task.FinalDataResultTask;
import task.FinishSimulationTask;
import task.LeaderSelectionTask;
import task.ReadjustmentTask;
import task.TurnOffRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class BaseStation extends Process {

    private static final double COMPUTE_SIZE = 0.0d;
    private static final double COMMUNICATION_SIZE = 0.0d;

    private static final int MAX_RANK = 1000;

    private static final double RECEIVE_TIMEOUT = 0.8;

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final double SIMULATION_TIMEOUT = 300000;

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final double LAST_RECEIVE_TIMEOUT = 120000;

    //The time when base station started listening for dispute messages
    private long disputeWaitStartTime = -1;

    private String currentLeader = null;
    private float currentMeasurement = -1;
    private ArrayList<String> disputingLeaderNodes = new ArrayList<>();
    private ArrayList<String> disputingMeasurementNodes = new ArrayList<>();
    private HashMap<String, Long> timeoutNodes = new HashMap<>();

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final long DISPUTE_TIMEOUT = 10000;

    private static final long BYZANTINE_TIMEOUT = 100000;

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
            sleep(3000);
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
            sleep(5000);
        } catch (HostFailureException e) {
            System.err.println("BaseStation host failed!!");
            return;
        }

        //Trigger initial leader selection
        for (int i = 0; i < nodeCount; i++) {
            LeaderSelectionTask leaderSelectionTask = null;
            if (i != id) {
                leaderSelectionTask = new LeaderSelectionTask("node_" + id, "node_" + i);
            }
            else {
                continue;
            }

            try {
                leaderSelectionTask.send("node_" + i);
            } catch (TransferFailureException | TimeoutException | HostFailureException e) {
                e.printStackTrace();
            }
        }

        long startTime = System.currentTimeMillis();
        long lastMeasurementReceivedTime = System.currentTimeMillis();

        while (true) {

            Task task = null;

            //RECEIVE LOOP
            try {
                task = Task.receive(Host.currentHost().getName(), RECEIVE_TIMEOUT);
            } catch (TransferFailureException | HostFailureException e) {
                System.err.println("Node " + id + " FAILED to transfer data\n" + e.getLocalizedMessage());
            } catch (TimeoutException ignored) {

            }

            if (System.currentTimeMillis() - startTime >= SIMULATION_TIMEOUT) {
                System.out.println("SIMULATION ENDING, BASE STATION NO LONGER LISTENING");
                break;
            }

            if (System.currentTimeMillis() - lastMeasurementReceivedTime > LAST_RECEIVE_TIMEOUT) {
                lastMeasurementReceivedTime = System.currentTimeMillis();

                //Flood with LeaderSelectionTask
                for (int i = 0; i < nodeCount; i++) {
                    LeaderSelectionTask leaderSelectionTask = new LeaderSelectionTask("node_" + id, "node_" + i);
                    boolean sent = false;
                    while (!sent) {
                        try {
                            leaderSelectionTask.send("node_" + i);
                            sent = true;
                        } catch (TransferFailureException | HostFailureException e) {
                            e.printStackTrace();
                        } catch (TimeoutException ignored) {
                        }
                    }
                }
            }

            if (disputeWaitStartTime > 0 && System.currentTimeMillis() - disputeWaitStartTime > DISPUTE_TIMEOUT) {
                if (disputingLeaderNodes.size() >= nodeCount / 2) {
                    System.out.println("BaseStation NODE " + id + " REJECTED FINAL MEASUREMENT RESULT: "
                        + currentMeasurement + " FROM NODE " + currentLeader);

                    //Timeout the malfunctioning leader
                    timeoutNodes.put(currentLeader, System.currentTimeMillis());
                }
                else if (disputingMeasurementNodes.size() > nodeCount / 2) {
                    lastMeasurementReceivedTime = System.currentTimeMillis();

                    //Flood with LeaderSelectionTask
                    for (int i = 0; i < nodeCount; i++) {
                        LeaderSelectionTask leaderSelectionTask = new LeaderSelectionTask("node_" + id, "node_" + i);
                        boolean sent = false;
                        while (!sent) {
                            try {
                                leaderSelectionTask.send("node_" + i);
                                sent = true;
                            } catch (TransferFailureException | HostFailureException e) {
                                e.printStackTrace();
                            } catch (TimeoutException ignored) {
                            }
                        }
                    }
                }
                else {
                    System.out.println("BaseStation NODE " + id + " ACCEPTED FINAL MEASUREMENT RESULT: "
                        + currentMeasurement + " FROM NODE " + currentLeader);

                    for (String disputingNode : disputingLeaderNodes) {
                        ReadjustmentTask readjustmentTask = new ReadjustmentTask();
                        readjustmentTask.setResult(currentMeasurement);
                        readjustmentTask.setLeader(currentLeader);
                        readjustmentTask.setOriginHost("node_" + id);
                        readjustmentTask.setDestinationHost(disputingNode);

                        boolean sent = false;
                        while (!sent) {
                            try {
                                readjustmentTask.send(disputingNode);
                                sent = true;
                            } catch (TransferFailureException | HostFailureException e) {
                                e.printStackTrace();
                            } catch (TimeoutException ignored) {
                            }
                        }
                    }

                    for (String disputingNode : disputingMeasurementNodes) {
                        ReadjustmentTask readjustmentTask = new ReadjustmentTask();
                        readjustmentTask.setResult(currentMeasurement);
                        readjustmentTask.setLeader(currentLeader);
                        readjustmentTask.setOriginHost("node_" + id);
                        readjustmentTask.setDestinationHost(disputingNode);

                        boolean sent = false;
                        while (!sent) {
                            try {
                                readjustmentTask.send(disputingNode);
                                sent = true;
                            } catch (TransferFailureException | HostFailureException e) {
                                e.printStackTrace();
                            } catch (TimeoutException ignored) {
                            }
                        }
                    }
                }

                disputeWaitStartTime = -1;
                currentLeader = null;
                currentMeasurement = -1;
                disputingLeaderNodes.clear();
                disputingMeasurementNodes.clear();
            }

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

                //Check if node is still in timeout
                if (timeoutNodes.containsKey(finalDataResultTask.getDestinationHost())) {
                    if (System.currentTimeMillis() - timeoutNodes.get(finalDataResultTask.getOriginHost()) > BYZANTINE_TIMEOUT)
                        timeoutNodes.remove(finalDataResultTask.getOriginHost());
                    else {
                        System.out.println("BaseStation NODE " + id + " IGNORED MEASUREMENT DUE TO NODE TIMEOUT");
                        continue;
                    }
                }

                lastMeasurementReceivedTime = System.currentTimeMillis();

                //Flood with DataAckTask
                for (int i = 0; i < nodeCount; i++) {
                    DataAckTask dataAckTask = new DataAckTask();
                    dataAckTask.setOriginHost("node_" + id);
                    dataAckTask.setDestinationHost("node_" + i);
                    dataAckTask.setResult(finalDataResultTask.getResult());
                    dataAckTask.setLeader(finalDataResultTask.getOriginHost());

                    boolean sent = false;
                    while (!sent) {
                        try {
                            dataAckTask.send("node_" + i);
                            sent = true;
                        } catch (TransferFailureException | HostFailureException e) {
                            e.printStackTrace();
                        } catch (TimeoutException ignored) {
                        }
                    }
                }

                disputeWaitStartTime = System.currentTimeMillis();
                currentLeader = finalDataResultTask.getOriginHost();
                currentMeasurement = finalDataResultTask.getResult();
                disputingLeaderNodes.clear();
                disputingMeasurementNodes.clear();
            }

            if (task != null && task instanceof DataDisputeTask) {
                DataDisputeTask dataDisputeTask = (DataDisputeTask) task;

                //Received measurement result without a measurement computation triggered
                if (disputeWaitStartTime < 0)
                    continue;

                //Accept only one result from each host
                if (!disputingLeaderNodes.contains(dataDisputeTask.getOriginHost()) &&
                        !dataDisputeTask.getLeader().equals(currentLeader)) {

                    disputingLeaderNodes.add(dataDisputeTask.getOriginHost());
                    System.out.println("BaseStation NODE " + id + " RECEIVED LEADER DISPUTE TASK FROM " +
                        dataDisputeTask.getOriginHost());
                }
                else if (!disputingMeasurementNodes.contains(dataDisputeTask.getOriginHost()) &&
                        Math.abs(currentMeasurement - dataDisputeTask.getResult()) > 0.01) {
                    disputingMeasurementNodes.add(dataDisputeTask.getOriginHost());
                    System.out.println("BaseStation NODE " + id + " RECEIVED RESULT DISPUTE TASK FROM " +
                        dataDisputeTask.getOriginHost());
                }
                else {
                    System.out.println("BaseStation NODE " + id + " RECEIVED INVALID DISPUTE TASK FROM " +
                        dataDisputeTask.getOriginHost());
                }
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