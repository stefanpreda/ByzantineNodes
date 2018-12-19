package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

public class Legit extends Process {

    // one leader election = 20s
    // one measurement = 20s
    // first leader election triggered at start
    // first measurement = 20s (first election) + 50s + 30s(duration)
    // successive leader elections = 20s(first) + 140s + 140s + etc
    // successive measurements = 20s + 50s + 50s + 50s (resets on leader election )

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final long LEADER_ELECTION_TIMEOUT = 10000;

    //Leader selection interval in millis (5m)
    private static final long LEADER_SELECTION_INTERVAL = 140000;

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final long MEASUREMENT_TIMEOUT = 10000;

    //In seconds
    private static final double RECEIVE_TIMEOUT = 1.0;

    //Measurement interval in millis (2m)
    private static final long MEASUREMENT_INTERVAL = 50000;

    //The minimum value for random generator
    private static final int MEASUREMENT_MIN = 10;

    //The maximum value for random generator
    private static final int MEASUREMENT_MAX = 30;

    //The currently elected leader
    private String currentLeader = null;

    //Internal ranks of other nodes
    private HashMap<String, Integer> ranks = new HashMap<>();

    //Current applications held in memory
    private HashMap<String, Integer> leadershipApplications = new HashMap<>();

    //Current results held in memory
    private HashMap<String, String> leadershipResults = new HashMap<>();

    //The time when the leader last triggered a leader selection
    private long lastLeaderSelectionTriggerTime = -1;

    //The timestamp when the initial flood with applications began
    private long leadershipSelectionApplicationStartTimestamp = -1;

    //The timestamp when the flood with results began
    private long leadershipSelectionResultStartTimestamp = -1;

    //If the current node flooded with his application
    private boolean leaderApplicationSent = false;

    //The leader determined by this host based on applications
    private String computedLeader = null;

    //The time when the leader last triggered a measurement
    private long lastMeasurementTriggerTime = System.currentTimeMillis();

    //The time when the flood with measurement messages started
    private long measurementFloodStartTime =  -1;

    //The time when the flood with measurement messages started
    private long measurementFloodResultStartTime =  -1;

    //Current measurements held in memory
    private HashMap<String, Integer> measurementResults = new HashMap<>();

    //Computed measurements from each node
    private HashMap<String, Float> measurementFinalResults = new HashMap<>();

    //Computed measurement by this node
    private float computedMeasurement = -1.0f;

    //Current measurement all nodes agreed upon
    private float currentMeasurement = -1.0f;

    public Legit(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 2)
            Msg.info("Wrong number of arguments for Legit node");
        int id = Integer.parseInt(args[0]);

        //The hostname of the base station
        String baseStationHostName = args[1];

        System.out.println("LEGIT NODE " + id + " STARTED");

        while (true) {

            Task task = null;

            //RECEIVE LOOP
            try {
                task = Task.receive(getHost().getName(), RECEIVE_TIMEOUT);
            } catch (TransferFailureException | HostFailureException e) {
                System.err.println("Node " + id + " FAILED to transfer data\n" + e.getLocalizedMessage());
            } catch (TimeoutException ignored) {

            }

            //Leader triggers new leader selection periodically only if a leader selection/measurement is not in progress
            if (currentLeader != null && currentLeader.equals(Host.currentHost().getName()) && !measurementOrElectionInProgress() &&
                (System.currentTimeMillis() - lastLeaderSelectionTriggerTime > LEADER_SELECTION_INTERVAL)) {

                //Flood with LeaderSelectionTask
                for (String destination : ranks.keySet()) {
                    if (!destination.equals(Host.currentHost().getName())) {
                        LeaderSelectionTask leaderSelectionTask = new LeaderSelectionTask();
                        leaderSelectionTask.setOriginHost(Host.currentHost().getName());
                        leaderSelectionTask.setDestinationHost(destination);
                        boolean sent = false;
                        while (!sent) {
                            try {
                                leaderSelectionTask.send(destination);
                                sent = true;
                            } catch (TransferFailureException | HostFailureException e) {
                                e.printStackTrace();
                            } catch (TimeoutException ignored) {
                            }
                        }
                    }
                }

                leadershipSelectionApplicationStartTimestamp = System.currentTimeMillis();
                leadershipSelectionResultStartTimestamp = -1;
                leadershipApplications.clear();
                leadershipResults.clear();
                leaderApplicationSent = false;
                computedLeader = null;

                lastLeaderSelectionTriggerTime = System.currentTimeMillis();
            }

            //LEADER APPLICATIONS FLOOD ENDED
            if (leadershipSelectionApplicationStartTimestamp > 0
                    && (System.currentTimeMillis() - leadershipSelectionApplicationStartTimestamp > LEADER_ELECTION_TIMEOUT)) {
                System.out.println("LEGIT NODE " + id + " FINISHED RECEIVING LEADER APPLICATIONS AND HAS THIS LIST: " + leadershipApplications);

                int maxRank = Integer.MIN_VALUE;

                for (String application : leadershipApplications.keySet())
                    if (leadershipApplications.get(application) > maxRank) {
                        maxRank = leadershipApplications.get(application);
                        computedLeader = application;
                    }

                if (computedLeader != null)
                    System.out.println("LEGIT NODE " + id + " COMPUTED " + computedLeader + " AS THE NEW LEADER");
                else
                    System.out.println("LEGIT NODE " + id + " DID NOT HAVE ANY LEADERSHIP APPLICATIONS");

                leadershipSelectionApplicationStartTimestamp = -1;
                leadershipApplications.clear();
                leaderApplicationSent = false;

                //Wait a bit for all nodes to compute new leader #TODO Wait time based on number of nodes
                try {
                    sleep(4000);
                } catch (HostFailureException e) {
                    System.err.println("BaseStation host failed!!");
                    return;
                }

                //Trigger the flood with results
                if (computedLeader != null) {
                    leadershipSelectionResultStartTimestamp = System.currentTimeMillis();

                    //Flood with leader result
                    for (String destination : ranks.keySet()) {
                        if (!destination.equals(Host.currentHost().getName())) {
                            LeaderResultTask leaderResultTask = new LeaderResultTask();
                            leaderResultTask.setHost(computedLeader);
                            leaderResultTask.setOriginHost(Host.currentHost().getName());
                            leaderResultTask.setDestinationHost(destination);
                            leaderResultTask.dsend(destination);

                            //Don't send them too fast
                            try {
                                sleep(500);
                            } catch (HostFailureException e) {
                                System.err.println("LEGIT" + id + " host failed!!");
                                return;
                            }

                        }
                    }

                    //Add own result
                    leadershipResults.put(Host.currentHost().getName(), computedLeader);
                }

                if (task == null || task instanceof LeadershipApplicationTask)
                    continue;
            }

            //LEADER RESULT FLOOD ENDED
            if (leadershipSelectionResultStartTimestamp > 0
                    && (System.currentTimeMillis() - leadershipSelectionResultStartTimestamp > LEADER_ELECTION_TIMEOUT)) {

                System.out.println("LEGIT NODE " + id + " FINISHED RECEIVING LEADER RESULTS AND HAS THIS LIST: " + leadershipResults);
                HashMap<String, Integer> voteCounts = new HashMap<>();
                String newLeader = null;
                int maxVotes = 0;

                //Count the votes
                for (String voter : leadershipResults.keySet()) {
                    int votes = 0;
                    if (voteCounts.get(leadershipResults.get(voter)) != null)
                        votes = voteCounts.get(leadershipResults.get(voter));

                    voteCounts.put(leadershipResults.get(voter), votes + 1);
                }

                //Find the winner
                for (String result : voteCounts.keySet())
                    if (voteCounts.get(result) > maxVotes) {
                        maxVotes = voteCounts.get(result);
                        newLeader = result;
                    }

                //Update the rank of the old leader (rotate between leaders)
                if (newLeader != null) {
                    if (currentLeader != null) {
                        int oldRank = ranks.get(currentLeader);
                        //#TODO CREATE CONSTANT, 1000 should be higher than the max possible rank of a node
                        ranks.put(currentLeader, oldRank - 1000);
                    }
                    currentLeader = newLeader;
                    System.out.println("LEGIT NODE " + id + " UPDATE LEADER: " + newLeader);
                }
                else {
                    System.out.println("LEGIT NODE " + id + " DID NOT RECEIVE ANY LEADER RESULTS");
                }

                //Reset everything
                leadershipSelectionApplicationStartTimestamp = -1;
                leadershipSelectionResultStartTimestamp = -1;
                leadershipApplications.clear();
                leadershipResults.clear();
                leaderApplicationSent = false;
                computedLeader = null;

                //Setup start time for measurement trigger
                lastMeasurementTriggerTime = System.currentTimeMillis();

                //Reset counter for starting a new leader selection
                lastLeaderSelectionTriggerTime = System.currentTimeMillis();

                if (task == null || task instanceof LeaderResultTask)
                    continue;
            }

            //Leader triggers measurement periodically only if a leader selection/measurement is not in progress
            if (currentLeader != null && currentLeader.equals(Host.currentHost().getName()) && !measurementOrElectionInProgress() &&
                    (System.currentTimeMillis() - lastMeasurementTriggerTime > MEASUREMENT_INTERVAL)) {
                lastMeasurementTriggerTime = System.currentTimeMillis();

                //Flood with measurement trigger messages
                for (String destination : ranks.keySet()) {
                    if (!destination.equals(Host.currentHost().getName())) {
                        TriggerDataCollectionTask triggerDataCollectionTask = new TriggerDataCollectionTask();
                        triggerDataCollectionTask.setOriginHost(Host.currentHost().getName());
                        triggerDataCollectionTask.setDestinationHost(destination);
                        boolean sent = false;

                        while (!sent) {
                            try {
                                triggerDataCollectionTask.send(destination);
                                sent = true;
                            } catch (TransferFailureException | HostFailureException e) {
                                e.printStackTrace();
                            } catch (TimeoutException ignored) {
                            }
                        }
                    }
                }

                //Wait for a while so all hosts receive the trigger message
                try {
                    sleep(4000);
                } catch (HostFailureException e) {
                    System.err.println("LEGIT" + id + " host failed!!");
                    return;
                }

                measurementFloodStartTime = System.currentTimeMillis();
                measurementResults.clear();
                measurementFinalResults.clear();
                computedMeasurement = -1.0f;

                int measurement = generateMeasurement();

                //Flood with measurements
                for (String destination : ranks.keySet()) {
                    if (!destination.equals(Host.currentHost().getName())) {
                        DataMeasurementTask dataMeasurementTask = new DataMeasurementTask();
                        dataMeasurementTask.setResult(measurement);
                        dataMeasurementTask.setOriginHost(Host.currentHost().getName());
                        dataMeasurementTask.setDestinationHost(destination);
                        dataMeasurementTask.dsend(destination);

                        //Don't send them so fast
                        try {
                            sleep(500);
                        } catch (HostFailureException e) {
                            System.err.println("LEGIT " + id + " host failed!!");
                            return;
                        }
                    }
                }

                //Also store own measurement
                measurementResults.put(Host.currentHost().getName(), measurement);
            }

            //MEASUREMENTS FLOOD ENDED
            if (measurementFloodStartTime > 0
                    && (System.currentTimeMillis() - measurementFloodStartTime > MEASUREMENT_TIMEOUT)) {

                System.out.println("LEGIT NODE " + id + " FINISHED RECEIVING MEASUREMENTS AND HAS THIS LIST: " + measurementResults);

                computedMeasurement = computeCommonValue();

                //Wait for a while so all hosts finish computing
                try {
                    sleep(4000);
                } catch (HostFailureException e) {
                    System.err.println("LEGIT" + id + " host failed!!");
                    return;
                }

                measurementFloodResultStartTime = System.currentTimeMillis();
                measurementFloodStartTime = -1;
                measurementResults.clear();
                measurementFinalResults.clear();

                //Flood with common measurement
                for (String destination : ranks.keySet()) {
                    if (!destination.equals(Host.currentHost().getName())) {
                        DataResultTask dataResultTask = new DataResultTask();
                        dataResultTask.setResult(computedMeasurement);
                        dataResultTask.setOriginHost(Host.currentHost().getName());
                        dataResultTask.setDestinationHost(destination);
                        dataResultTask.dsend(destination);

                        //Don't send them so fast
                        try {
                            sleep(500);
                        } catch (HostFailureException e) {
                            System.err.println("LEGIT " + id + " host failed!!");
                            return;
                        }
                    }
                }

                //Add own computed value
                measurementFinalResults.put(Host.currentHost().getName(), computedMeasurement);

                if (task == null || task instanceof DataMeasurementTask)
                    continue;
            }

            //MEASUREMENTS RESULT FLOOD ENDED
            if (measurementFloodResultStartTime > 0
                    && (System.currentTimeMillis() - measurementFloodResultStartTime > MEASUREMENT_TIMEOUT)) {

                System.out.println("LEGIT NODE " + id + " FINISHED RECEIVING MEASUREMENT RESULTS AND HAS THIS LIST: " + measurementFinalResults);

                HashMap<Float, Integer> votes = new HashMap<>();

                for (String host : measurementFinalResults.keySet()) {
                    Integer num_votes = votes.get(measurementFinalResults.get(host));
                    if (num_votes == null)
                        votes.put(measurementFinalResults.get(host), 1);
                    else
                        votes.put(measurementFinalResults.get(host), num_votes + 1);
                }

                int maxVotes = Integer.MIN_VALUE;
                float newMeasurement = -1.0f;

                Iterator<Float> it = votes.keySet().iterator();

                while (it.hasNext()) {
                    Float value = it.next();
                    Integer voteCount = votes.get(value);

                    if (voteCount > maxVotes) {
                        maxVotes = voteCount;
                        newMeasurement = value;
                    }
                }

                if (newMeasurement > 0) {
                    currentMeasurement = newMeasurement;
                    System.out.println("LEGIT NODE " + id + " UPDATE MEASUREMENT: " + newMeasurement);

                    if (currentLeader.equals(Host.currentHost().getName())) {
                        FinalDataResultTask finalDataResultTask = new FinalDataResultTask();
                        finalDataResultTask.setResult(currentMeasurement);
                        finalDataResultTask.setOriginHost(Host.currentHost().getName());
                        finalDataResultTask.setDestinationHost(baseStationHostName);
                        finalDataResultTask.dsend(baseStationHostName);

                        //wait a bit after sending
                        try {
                            sleep(500);
                        } catch (HostFailureException e) {
                            System.err.println("LEGIT " + id + " host failed!!");
                            return;
                        }
                    }
                }
                else {
                    System.out.println("LEGIT NODE " + id + " DID NOT RECEIVE ANY MEASUREMENT RESULTS");
                }

                measurementFloodResultStartTime = -1;
                measurementFloodStartTime = -1;
                measurementResults.clear();
                measurementFinalResults.clear();
                computedMeasurement = -1.0f;

                if (task == null || task instanceof DataResultTask)
                    continue;
            }

            if (task != null) {

                //Received FinishSimulationTask from base station
                if (task instanceof FinishSimulationTask) {
                    break;
                }

                if (task instanceof ActivationTask) {
                    ActivationTask activationTask = (ActivationTask)task;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (activationTask.getOriginHost().equals(baseStationHostName)) {
                        ranks.putAll(activationTask.getRanks());
                        System.out.println("LEGIT NODE " + id + " RECEIVED ACTIVATION TASK FROM BASE STATION WITH THESE RANKS: " + ranks);
                    }
                }

                if (task instanceof LeaderSelectionTask) {
                    LeaderSelectionTask leaderSelectionTask = (LeaderSelectionTask) task;
                    boolean valid = false;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (leaderSelectionTask.getOriginHost().equals(baseStationHostName)) {
                        System.out.println("LEGIT NODE " + id + " RECEIVED LEADER SELECTION TASK FROM BASE STATION");
                        valid = true;
                    }

                    //Check if the message came from the current leader
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (currentLeader != null && leaderSelectionTask.getOriginHost().equals(currentLeader)) {
                        System.out.println("LEGIT NODE " + id + " RECEIVED LEADER SELECTION TASK FROM CURRENT LEADER: " + currentLeader);
                        valid = true;
                    }

                    //Most likely received from byzantine node
                    if (!valid)
                        continue;

                    //Wait a bit for all nodes to receive the leader selection message #TODO Wait time based on number of nodes
                    try {
                        sleep(4000);
                    } catch (HostFailureException e) {
                        System.err.println("BaseStation host failed!!");
                        return;
                    }

                    //This is a valid leader selection, setup the start time if there isn't an ongoing leader selection
                    if (leadershipSelectionApplicationStartTimestamp < 0) {
                        leadershipSelectionApplicationStartTimestamp = System.currentTimeMillis();
                    }

                    //Also setup the start time if something happened and the previous leader selection was not ended for this node
                    //Use 3 times the timeout, once for application, once for decision process, once for result flooding
                    if (System.currentTimeMillis() - leadershipSelectionApplicationStartTimestamp > 3 * LEADER_ELECTION_TIMEOUT) {

                        System.out.println("LEGIT NODE " + id +" RESTARTED LEADER SELECTION PROCESS");

                        leadershipSelectionApplicationStartTimestamp = System.currentTimeMillis();
                        leadershipSelectionResultStartTimestamp = -1;
                        leadershipApplications.clear();
                        leadershipResults.clear();
                        leaderApplicationSent = false;
                        computedLeader = null;
                    }

                    lastLeaderSelectionTriggerTime = System.currentTimeMillis();

                    //Flood with applications only if not the current leader and did not flood already
                    if (currentLeader == null ||
                            (!currentLeader.equals(Host.currentHost().getName()) && !leaderApplicationSent)) {
                        leaderApplicationSent = true;

                        //Flood with leadership applications
                        for (String destination : ranks.keySet()) {
                            if (!destination.equals(Host.currentHost().getName())) {
                                LeadershipApplicationTask leadershipApplicationTask = new LeadershipApplicationTask();
                                leadershipApplicationTask.setRank(ranks.get(Host.currentHost().getName()));
                                leadershipApplicationTask.setOriginHost(Host.currentHost().getName());
                                leadershipApplicationTask.setDestinationHost(destination);
                                leadershipApplicationTask.dsend(destination);

                                //Don't send them so fast
                                try {
                                    sleep(500);
                                } catch (HostFailureException e) {
                                    System.err.println("LEGIT " + id + " host failed!!");
                                    return;
                                }
                            }
                        }

                        //Add myself in the list of applications
                        leadershipApplications.put(Host.currentHost().getName(), ranks.get(Host.currentHost().getName()));
                    }

                }

                if (task instanceof LeadershipApplicationTask) {
                    LeadershipApplicationTask leadershipApplicationTask = (LeadershipApplicationTask)task;

                    //Received leadership application without a leadership selection triggered
                    if (leadershipSelectionApplicationStartTimestamp < 0)
                        continue;

                    //Check if the rank received in the application is bigger than the one in memory
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (ranks.get(leadershipApplicationTask.getOriginHost()) > leadershipApplicationTask.getRank()) {
                        System.out.println("LEGIT NODE " + id + " RECEIVED LEADER APPLICATION TASK FROM " +
                                leadershipApplicationTask.getOriginHost() + " BUT RANK IS HIGHER THAN EXPECTED");
                        continue;
                    }

                    //Check if the rank received in the application is smaller than the one in memory and update it in memory
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (ranks.get(leadershipApplicationTask.getOriginHost()) > leadershipApplicationTask.getRank()) {
                        System.out.println("LEGIT NODE " + id + " RECEIVED LEADER APPLICATION TASK FROM " +
                                leadershipApplicationTask.getOriginHost() + " BUT RANK IS SMALLER THAN EXPECTED AND UPDATED");
                        ranks.put(leadershipApplicationTask.getOriginHost(), leadershipApplicationTask.getRank());
                    }

                    //Save the application
                    leadershipApplications.put(leadershipApplicationTask.getOriginHost(), leadershipApplicationTask.getRank());

                    System.out.println("LEGIT NODE " + id + " RECEIVED LEADER APPLICATION TASK FROM " +
                            leadershipApplicationTask.getOriginHost());

                }

                if (task instanceof LeaderResultTask) {
                    LeaderResultTask leaderResultTask = (LeaderResultTask)task;

                    //Received leadership result without a leadership selection triggered
                    if (leadershipSelectionResultStartTimestamp < 0)
                        continue;


                    //Accept only one result from each host
                    if (leadershipResults.get(leaderResultTask.getOriginHost()) == null) {
                        leadershipResults.put(leaderResultTask.getOriginHost(), leaderResultTask.getHost());

                        System.out.println("LEGIT NODE " + id + " RECEIVED LEADER RESULT TASK FROM " +
                                leaderResultTask.getOriginHost());
                    }
                    else {
                        System.out.println("LEGIT NODE " + id + " RECEIVED DUPLICATE LEADER RESULT TASK FROM " +
                                leaderResultTask.getOriginHost());
                    }
                }

                if (task instanceof TriggerDataCollectionTask) {
                    TriggerDataCollectionTask triggerDataCollectionTask = (TriggerDataCollectionTask) task;
                    boolean valid = true;

                    //Check if the message came from the current leader
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (currentLeader == null || !triggerDataCollectionTask.getOriginHost().equals(currentLeader)) {
                        valid = false;
                    }

                    //Received measurement task during leader selection process
                    if (leadershipSelectionApplicationStartTimestamp > 0 || leadershipSelectionResultStartTimestamp > 0) {
                        valid = false;
                    }

                    if (!valid)
                        continue;

                    System.out.println("LEGIT NODE " + id + " RECEIVED MEASUREMENT TRIGGER TASK FROM " +
                            triggerDataCollectionTask.getOriginHost());

                    //Wait a bit for all nodes to receive the trigger message #TODO Wait time based on number of nodes
                    try {
                        sleep(4000);
                    } catch (HostFailureException e) {
                        System.err.println("LEGIT" + id + " host failed!!");
                        return;
                    }

                    //Ignore this request if it is too frequent
                    if (lastMeasurementTriggerTime == -1 || System.currentTimeMillis() - lastMeasurementTriggerTime > MEASUREMENT_INTERVAL)
                        lastMeasurementTriggerTime = System.currentTimeMillis();
                    else
                        continue;

                    measurementFloodStartTime = System.currentTimeMillis();
                    measurementResults.clear();
                    measurementFinalResults.clear();
                    computedMeasurement = -1.0f;

                    //Generate a random value within bounds
                    int measurement = generateMeasurement();

                    //Flood with measurements
                    for (String destination : ranks.keySet()) {
                        if (!destination.equals(Host.currentHost().getName())) {
                            DataMeasurementTask dataMeasurementTask = new DataMeasurementTask();
                            dataMeasurementTask.setResult(measurement);
                            dataMeasurementTask.setOriginHost(Host.currentHost().getName());
                            dataMeasurementTask.setDestinationHost(destination);
                            dataMeasurementTask.dsend(destination);

                            //Don't send them so fast
                            try {
                                sleep(500);
                            } catch (HostFailureException e) {
                                System.err.println("LEGIT " + id + " host failed!!");
                                return;
                            }
                        }
                    }

                    //Also store own measurement
                    measurementResults.put(Host.currentHost().getName(), measurement);
                }

                if (task instanceof DataMeasurementTask) {
                    DataMeasurementTask dataMeasurementTask = (DataMeasurementTask) task;

                    if (measurementFloodStartTime < 0)
                        continue;

                    //Accept only one result from each host
                    if (measurementResults.get(dataMeasurementTask.getOriginHost()) == null) {
                        measurementResults.put(dataMeasurementTask.getOriginHost(), dataMeasurementTask.getResult());

                        System.out.println("LEGIT NODE " + id + " RECEIVED MEASUREMENT TASK FROM " +
                                dataMeasurementTask.getOriginHost());
                    }
                    else {
                        System.out.println("LEGIT NODE " + id + " RECEIVED DUPLICATE MEASUREMENT TASK FROM " +
                                dataMeasurementTask.getOriginHost());
                    }
                }

                if (task instanceof DataResultTask) {
                    DataResultTask dataResultTask = (DataResultTask)task;

                    //Received measurement result without a measurement computation triggered
                    if (measurementFloodResultStartTime < 0)
                        continue;

                    //Accept only one result from each host
                    if (measurementFinalResults.get(dataResultTask.getOriginHost()) == null) {
                        measurementFinalResults.put(dataResultTask.getOriginHost(), dataResultTask.getResult());

                        System.out.println("LEGIT NODE " + id + " RECEIVED MEASUREMENT RESULT TASK FROM " +
                                dataResultTask.getOriginHost());
                    }
                    else {
                        System.out.println("LEGIT NODE " + id + " RECEIVED DUPLICATE MEASUREMENT RESULT TASK FROM " +
                                dataResultTask.getOriginHost());
                    }
                }

                if (task instanceof DataAckTask) {
                    DataAckTask dataAckTask = (DataAckTask) task;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (dataAckTask.getOriginHost().equals(baseStationHostName)) {
                        System.out.println("LEGIT NODE " + id + " RECEIVED DATA ACK TASK FROM BASE STATION");
                    }
                    else {
                        continue;
                    }

                    if (!dataAckTask.getLeader().equals(currentLeader) || Math.abs(dataAckTask.getResult() - currentMeasurement) > 0.01) {
                        DataDisputeTask dataDisputeTask = new DataDisputeTask();
                        dataDisputeTask.setResult(currentMeasurement);
                        dataDisputeTask.setLeader(currentLeader);
                        dataDisputeTask.setOriginHost(Host.currentHost().getName());
                        dataDisputeTask.setDestinationHost(baseStationHostName);
                        dataDisputeTask.dsend(baseStationHostName);
                    }
                }

                if (task instanceof ReadjustmentTask) {
                    ReadjustmentTask readjustmentTask = (ReadjustmentTask) task;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (readjustmentTask.getOriginHost().equals(baseStationHostName)) {
                        System.out.println("LEGIT NODE " + id + " RECEIVED READJUSTMENT TASK FROM BASE STATION" +
                            " WITH LEADER: " + readjustmentTask.getLeader() + " MEASUREMENT: " + readjustmentTask.getResult());
                    }
                    else {
                        continue;
                    }

                    currentMeasurement = readjustmentTask.getResult();
                    currentLeader = readjustmentTask.getLeader();
                }
            }
        }

        System.out.println("LEGIT NODE " + id + " FINISHED");
    }

    private int generateMeasurement() {
        Random random = new Random();

        return MEASUREMENT_MIN + random.nextInt(MEASUREMENT_MAX - MEASUREMENT_MIN);
    }

    private float computeCommonValue() {
        ArrayList<Integer> legitValues = new ArrayList<>();

        int count = measurementResults.keySet().size() > 0 ? measurementResults.keySet().size() : 1;
        int sum = 0;

        for (String host : measurementResults.keySet())
            sum += measurementResults.get(host);

        if (count > 1) {
            for (String host : measurementResults.keySet()) {
                float average = (sum - measurementResults.get(host)) / (count - 1);
                if ((measurementResults.get(host) - average) < (MEASUREMENT_MAX - MEASUREMENT_MIN))
                    legitValues.add(measurementResults.get(host));
            }
        }
        else {
            return computedMeasurement;
        }

        count = legitValues.size();
        sum = 0;

        for (Integer i : legitValues)
            sum += i;

        return (float)sum / count;

    }

    private boolean measurementOrElectionInProgress() {

        if (leadershipSelectionApplicationStartTimestamp < 0 && leadershipSelectionResultStartTimestamp < 0 &&
            measurementFloodStartTime < 0 && measurementFloodResultStartTime < 0)
            return false;

        return true;
    }
}
