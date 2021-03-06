package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class Byzantine extends Process {

    private static final int NODE_COUNT = 10;

    //Higher than the highest possible rank
    private static final int ROLLING_RANK_VALUE = 1000;

    //In millis
    private static final long LEADER_ELECTION_TIMEOUT = 1600 * NODE_COUNT;

    //Leader selection interval in millis (5m)
    private static final long LEADER_SELECTION_INTERVAL = 23000 * NODE_COUNT;

    //In millis
    private static final long MEASUREMENT_TIMEOUT = 1600 * NODE_COUNT;

    //In seconds
    private static final double RECEIVE_TIMEOUT = 3.0;

    //Measurement interval in millis (2m)
    private static final long MEASUREMENT_INTERVAL = 6000 * NODE_COUNT;

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
    private long lastLeaderSelectionTriggerTime = System.currentTimeMillis();

    //The timestamp when the initial flood with applications began
    private long leadershipSelectionApplicationStartTimestamp = -1;

    //The timestamp when the flood with results began
    private long leadershipSelectionResultStartTimestamp = -1;

    //If the current node flooded with his application
    private boolean leaderApplicationSent = false;

    //The leader determined by this host based on applications
    private String computedLeader = null;

    //The time when the leader last triggered a measurement
    private long lastMeasurementTriggerTime = -1;

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

    //Byzantine Behaviour flags
    private ArrayList<String> ignoreLeaderSelectionNodes =  new ArrayList<>(Arrays.asList("node_7", "node_8", "node_9"));
    private ArrayList<String> ignoreDataMeasurementNodes =  new ArrayList<>(Arrays.asList("node_not_exists", "node_not_exists_again"));
    private ArrayList<String> ignoreDataResultNodes =  new ArrayList<>(Arrays.asList("node_not_exists", "node_not_exists_again"));
    private boolean currentLeaderDiesAfterElection = false;
    private boolean currentLeaderEnrollsAgain = true;
    private float differentValueSentToBaseStation = 100.0f;

    private static boolean leaderSpamsMeasurementTriggers = false;
    private static final int leaderSpamsMeasurementTriggersDelay = 5000;
    private static int leaderSpamsMeasurementTriggersCount = 0;

    private Map<String, Integer> differentRanksNodes = new HashMap<String, Integer>(){{
        this.put("node_not_exists", 10);
        this.put("node_not_exists_again", 1001);
    }};
    private Map<String, String> differentComputedLeaderNodes = new HashMap<String, String>(){{
        this.put("node_not_exists", "node_8");
        this.put("node_not_exists_again", "node_9");
    }};
    private Map<String, String> differentCurrentLeaderNodes = new HashMap<String, String>(){{
        this.put("node_not_exists", "node_0");
        this.put("node_not_exists_again", "node_1");
    }};
    private Map<String, Integer> differentDataMeasurementNodes = new HashMap<String, Integer>(){{
        this.put("node_not_exists", 4000);
        this.put("node_not_exists_again", 4000);
    }};
    private Map<String, Float> differentDataResultNodes = new HashMap<String, Float>(){{
        this.put("node_not_exists", 4000.0f);
        this.put("node_not_exists_again", 4000.0f);
    }};
    private Map<String, Float> differentCurrentMeasurementNodes = new HashMap<String, Float>(){{
        this.put("node_not_exists", 5000.0f);
        this.put("node_not_exists_again", 4000.0f);
    }};
    private Map<String, Float> trySendingToBaseStationNodes = new HashMap<String, Float>(){{
        this.put("node_not_exists", 5000.0f);
        this.put("node_not_exists_again", 4000.0f);
    }};
    private static final int trySendingToBaseStationDelay = 15000;
    private long trySendingToBaseStationLastAttemptTimestamp = -1;
    private int trySendingToBaseStationCount = 2;

    private Map<String, Class> differentMessagesAtLeaderApplicationNodes = new HashMap<String, Class>(){{
        this.put("node_not_exists", DataMeasurementTask.class);
        this.put("node_not_exists_again", DataResultTask.class);
    }};
    private Map<String, Class> differentMessagesAtLeaderResultNodes = new HashMap<String, Class>(){{
        this.put("node_not_exists", DataMeasurementTask.class);
        this.put("node_not_exists_again", DataResultTask.class);
    }};
    private Map<String, Class> differentMessagesAtDataMeasurementNodes = new HashMap<String, Class>(){{
        this.put("node_not_exists", LeaderResultTask.class);
        this.put("node_not_exists_again", LeadershipApplicationTask.class);
    }};
    private Map<String, Class> differentMessagesAtDataResultNodes = new HashMap<String, Class>(){{
        this.put("node_not_exists", LeaderResultTask.class);
        this.put("node_not_exists_again", LeadershipApplicationTask.class);
    }};


    public Byzantine(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 2)
            Msg.info("Wrong number of arguments for BYZANTINE node");
        int id = Integer.parseInt(args[0]);

        //The hostname of the base station
        String baseStationHostName = args[1];

        Host.setAsyncMailbox(Host.currentHost().getName());

        System.out.println("BYZANTINE NODE " + id + " STARTED");

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

                System.out.println("LEADER TRYING TO TRIGGER LEADER SELECTION");

                //Flood with LeaderSelectionTask
                for (String destination : ranks.keySet()) {
                    if (!destination.equals(Host.currentHost().getName())) {
                        LeaderSelectionTask leaderSelectionTask = new LeaderSelectionTask();
                        leaderSelectionTask.setOriginHost(Host.currentHost().getName());
                        leaderSelectionTask.setDestinationHost(destination);

                        timeoutSendWithRetries(leaderSelectionTask, destination);
                    }
                }

                leadershipSelectionApplicationStartTimestamp = System.currentTimeMillis();
                leadershipSelectionResultStartTimestamp = -1;
                leadershipApplications.clear();
                leadershipResults.clear();
                leaderApplicationSent = false;
                computedLeader = null;

                lastLeaderSelectionTriggerTime = System.currentTimeMillis();

                if (currentLeaderEnrollsAgain) {
                    currentLeaderEnrollsAgain = false;
                    leaderApplicationSent = true;

                    //Flood with leadership applications
                    for (String destination : ranks.keySet()) {
                        //Don't send to localhost
                        if (!Host.currentHost().getName().equals(destination)) {
                            if (!destination.equals(Host.currentHost().getName())) {
                                LeadershipApplicationTask leadershipApplicationTask = new LeadershipApplicationTask();
                                leadershipApplicationTask.setRank(ranks.get(Host.currentHost().getName()));
                                leadershipApplicationTask.setOriginHost(Host.currentHost().getName());
                                leadershipApplicationTask.setDestinationHost(destination);

                                if (differentRanksNodes.containsKey(Host.currentHost().getName())) {
                                    leadershipApplicationTask.setRank(differentRanksNodes.get(Host.currentHost().getName()));
                                }

                                timeoutSendWithRetries(leadershipApplicationTask, destination);
                            }
                        }
                    }

                    //Add myself in the list of applications
                    if (differentRanksNodes.containsKey(Host.currentHost().getName())) {
                        leadershipApplications.put(Host.currentHost().getName(), differentRanksNodes.get(Host.currentHost().getName()));
                        differentRanksNodes.remove(Host.currentHost().getName());
                    }
                    else {
                        leadershipApplications.put(Host.currentHost().getName(), ranks.get(Host.currentHost().getName()));
                    }
                }
            }

            //LEADER APPLICATIONS FLOOD ENDED
            if (leadershipSelectionApplicationStartTimestamp > 0
                    && (System.currentTimeMillis() - leadershipSelectionApplicationStartTimestamp > LEADER_ELECTION_TIMEOUT)) {
                System.out.println("BYZANTINE NODE " + id + " FINISHED RECEIVING LEADER APPLICATIONS AND HAS THIS LIST: " + leadershipApplications);

                int maxRank = Integer.MIN_VALUE;

                for (String application : leadershipApplications.keySet())
                    if (leadershipApplications.get(application) > maxRank) {
                        maxRank = leadershipApplications.get(application);
                        computedLeader = application;
                    }

                if (computedLeader != null) {
                    if (differentComputedLeaderNodes.containsKey(Host.currentHost().getName())) {
                        System.out.println("BYZANTINE NODE " + id + " COMPUTED " + differentComputedLeaderNodes.get(Host.currentHost().getName()) +
                            " AS THE NEW LEADER");
                        computedLeader = differentComputedLeaderNodes.get(Host.currentHost().getName());
                        differentComputedLeaderNodes.remove(Host.currentHost().getName());
                    }
                    else
                        System.out.println("BYZANTINE NODE " + id + " COMPUTED " + computedLeader + " AS THE NEW LEADER");
                }
                else
                    System.out.println("BYZANTINE NODE " + id + " DID NOT HAVE ANY LEADERSHIP APPLICATIONS");

                leadershipSelectionApplicationStartTimestamp = -1;
                leadershipApplications.clear();
                leaderApplicationSent = false;

                //Trigger the flood with results
                if (computedLeader != null) {
                    leadershipSelectionResultStartTimestamp = System.currentTimeMillis();

                    //wait a bit before sending
                    try {
                        sleep(600);
                    } catch (HostFailureException e) {
                        System.err.println("BYZANTINE " + id + " host failed!!");
                        return;
                    }

                    //Flood with leader result
                    if (differentMessagesAtLeaderResultNodes.containsKey(Host.currentHost().getName())) {
                        System.out.println("BYZANTINE NODE " + id + " WILL SEND A DIFFERENT MESSAGE TYPE");
                        Class differentMessageClass = differentMessagesAtLeaderResultNodes.get(Host.currentHost().getName());
                        try {
                            for (String destination : ranks.keySet()) {
                                if (!destination.equals(Host.currentHost().getName())) {
                                    SimpleTask differentMessage = (SimpleTask)differentMessageClass.getDeclaredConstructor().newInstance();
                                    differentMessage.setOriginHost(Host.currentHost().getName());
                                    differentMessage.setDestinationHost(destination);
                                    timeoutSendWithRetries(differentMessage, destination);
                                }
                            }

                        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                            e.printStackTrace();
                        }

                        differentMessagesAtLeaderResultNodes.remove(Host.currentHost().getName());
                    }
                    else {
                        for (String destination : ranks.keySet()) {
                            if (!destination.equals(Host.currentHost().getName())) {
                                LeaderResultTask leaderResultTask = new LeaderResultTask();
                                leaderResultTask.setHost(computedLeader);
                                leaderResultTask.setOriginHost(Host.currentHost().getName());
                                leaderResultTask.setDestinationHost(destination);

                                timeoutSendWithRetries(leaderResultTask, destination);
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

                System.out.println("BYZANTINE NODE " + id + " FINISHED RECEIVING LEADER RESULTS AND HAS THIS LIST: " + leadershipResults);
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
                        ranks.put(currentLeader, oldRank - ROLLING_RANK_VALUE);
                    }
                    if (differentCurrentLeaderNodes.containsKey(Host.currentHost().getName())) {
                        currentLeader = differentCurrentLeaderNodes.get(Host.currentHost().getName());
                        System.out.println("BYZANTINE NODE " + id + " UPDATE LEADER: " + differentCurrentLeaderNodes.get(Host.currentHost().getName()));
                        differentCurrentLeaderNodes.remove(Host.currentHost().getName());
                    }
                    else {
                        currentLeader = newLeader;
                        System.out.println("BYZANTINE NODE " + id + " UPDATE LEADER: " + newLeader);
                    }
                }
                else {
                    System.out.println("BYZANTINE NODE " + id + " DID NOT RECEIVE ANY LEADER RESULTS");
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

                //Setup start time for byzantine node behavior of sending without decision to base station
                trySendingToBaseStationLastAttemptTimestamp = System.currentTimeMillis();

                if (!currentLeader.equals(Host.currentHost().getName())) {
                    leaderSpamsMeasurementTriggers = false;
                    currentLeaderEnrollsAgain = false;
                }

                if (currentLeaderDiesAfterElection && currentLeader.equals(Host.currentHost().getName())) {
                    currentLeaderDiesAfterElection = false;
                    TurnOffRequest turnOffRequest = new TurnOffRequest(Host.currentHost().getName());
                    timeoutSendWithRetries(turnOffRequest, baseStationHostName);
                }
                else
                    currentLeaderDiesAfterElection = false;

                if (task == null || task instanceof LeaderResultTask)
                    continue;
            }

            //Leader triggers measurement periodically only if a leader selection/measurement is not in progress
            if (currentLeader != null && currentLeader.equals(Host.currentHost().getName()) && !measurementOrElectionInProgress() &&
                    (System.currentTimeMillis() - lastMeasurementTriggerTime > MEASUREMENT_INTERVAL)) {
                lastMeasurementTriggerTime = System.currentTimeMillis();

                System.out.println("LEADER TRYING TO TRIGGER MEASUREMENT");

                //Flood with measurement trigger messages
                for (String destination : ranks.keySet()) {
                    if (!destination.equals(Host.currentHost().getName())) {
                        TriggerDataCollectionTask triggerDataCollectionTask = new TriggerDataCollectionTask();
                        triggerDataCollectionTask.setOriginHost(Host.currentHost().getName());
                        triggerDataCollectionTask.setDestinationHost(destination);

                        timeoutSendWithRetries(triggerDataCollectionTask, destination);
                    }
                }

                measurementFloodStartTime = System.currentTimeMillis();
                measurementResults.clear();
                measurementFinalResults.clear();
                computedMeasurement = -1.0f;

                //Wait a bit for all nodes to receive the message
                try {
                    sleep(600 * NODE_COUNT);
                } catch (HostFailureException e) {
                    System.err.println("Byzantine node " + id + "host failed!!");
                    return;
                }

                int measurement = generateMeasurement();

                if (differentDataMeasurementNodes.containsKey(Host.currentHost().getName())) {
                    measurement = differentDataMeasurementNodes.get(Host.currentHost().getName());
                    differentDataMeasurementNodes.remove(Host.currentHost().getName());
                }

                if (leaderSpamsMeasurementTriggersCount > 0) {
                    leaderSpamsMeasurementTriggers = true;
                }


                if (differentMessagesAtDataMeasurementNodes.containsKey(Host.currentHost().getName())) {
                    System.out.println("BYZANTINE NODE " + id + " WILL SEND A DIFFERENT MESSAGE TYPE");
                    Class differentMessageClass = differentMessagesAtDataMeasurementNodes.get(Host.currentHost().getName());
                    try {
                        for (String destination : ranks.keySet()) {
                            if (!destination.equals(Host.currentHost().getName())) {
                                SimpleTask differentMessage = (SimpleTask)differentMessageClass.getDeclaredConstructor().newInstance();
                                differentMessage.setOriginHost(Host.currentHost().getName());
                                differentMessage.setDestinationHost(destination);
                                timeoutSendWithRetries(differentMessage, destination);
                            }
                        }

                    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                        e.printStackTrace();
                    }

                    differentMessagesAtDataMeasurementNodes.remove(Host.currentHost().getName());
                }
                else {
                    //Flood with measurements
                    for (String destination : ranks.keySet()) {
                        if (!destination.equals(Host.currentHost().getName())) {
                            DataMeasurementTask dataMeasurementTask = new DataMeasurementTask();
                            dataMeasurementTask.setResult(measurement);
                            dataMeasurementTask.setOriginHost(Host.currentHost().getName());
                            dataMeasurementTask.setDestinationHost(destination);
                            timeoutSendWithRetries(dataMeasurementTask, destination);
                        }
                    }
                }

                //Also store own measurement
                measurementResults.put(Host.currentHost().getName(), measurement);
            }

            //Spam with measurement triggers
            if (leaderSpamsMeasurementTriggers && leaderSpamsMeasurementTriggersCount > 0 && currentLeader != null
                && currentLeader.equals(Host.currentHost().getName()) &&
                (System.currentTimeMillis() - lastMeasurementTriggerTime > leaderSpamsMeasurementTriggersDelay)) {

                leaderSpamsMeasurementTriggersCount--;

                System.out.println("BYZANTINE NODE " + id + " SPAMS MEASUREMENT TRIGGER MESSAGES");

                //Flood with measurement trigger messages
                for (String destination : ranks.keySet()) {
                    if (!destination.equals(Host.currentHost().getName())) {
                        TriggerDataCollectionTask triggerDataCollectionTask = new TriggerDataCollectionTask();
                        triggerDataCollectionTask.setOriginHost(Host.currentHost().getName());
                        triggerDataCollectionTask.setDestinationHost(destination);

                        timeoutSendWithRetries(triggerDataCollectionTask, destination);
                    }
                }
            }

            //MEASUREMENTS FLOOD ENDED
            if (measurementFloodStartTime > 0
                    && (System.currentTimeMillis() - measurementFloodStartTime > MEASUREMENT_TIMEOUT)) {

                System.out.println("BYZANTINE NODE " + id + " FINISHED RECEIVING MEASUREMENTS AND HAS THIS LIST: " + measurementResults);

                computedMeasurement = computeCommonValue();

                if (differentDataResultNodes.containsKey(Host.currentHost().getName())) {
                    computedMeasurement = differentDataResultNodes.get(Host.currentHost().getName());
                    differentDataResultNodes.remove(Host.currentHost().getName());
                }

                measurementFloodResultStartTime = System.currentTimeMillis();
                measurementFloodStartTime = -1;
                measurementResults.clear();
                measurementFinalResults.clear();

                //Wait for a while so all hosts finish computing
                try {
                    sleep(600);
                } catch (HostFailureException e) {
                    System.err.println("BYZANTINE" + id + " host failed!!");
                    return;
                }

                if (ignoreDataResultNodes.contains(Host.currentHost().getName())) {
                    System.out.println("BYZANTINE NODE " + id + " DOES NOT SEND RESULT DATA");
                    ignoreDataResultNodes.remove(Host.currentHost().getName());
                    continue;
                }

                //Flood with common measurement
                if (differentMessagesAtDataResultNodes.containsKey(Host.currentHost().getName())) {
                    System.out.println("BYZANTINE NODE " + id + " WILL SEND A DIFFERENT MESSAGE TYPE");
                    Class differentMessageClass = differentMessagesAtDataResultNodes.get(Host.currentHost().getName());
                    try {
                        for (String destination : ranks.keySet()) {
                            if (!destination.equals(Host.currentHost().getName())) {
                                SimpleTask differentMessage = (SimpleTask)differentMessageClass.getDeclaredConstructor().newInstance();
                                differentMessage.setOriginHost(Host.currentHost().getName());
                                differentMessage.setDestinationHost(destination);
                                timeoutSendWithRetries(differentMessage, destination);
                            }
                        }

                    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                        e.printStackTrace();
                    }

                    differentMessagesAtDataResultNodes.remove(Host.currentHost().getName());
                }
                else {
                    for (String destination : ranks.keySet()) {
                        if (!destination.equals(Host.currentHost().getName())) {
                            DataResultTask dataResultTask = new DataResultTask();
                            dataResultTask.setResult(computedMeasurement);
                            dataResultTask.setOriginHost(Host.currentHost().getName());
                            dataResultTask.setDestinationHost(destination);
                            timeoutSendWithRetries(dataResultTask, destination);
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

                System.out.println("BYZANTINE NODE " + id + " FINISHED RECEIVING MEASUREMENT RESULTS AND HAS THIS LIST: " + measurementFinalResults);

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

                    if (differentCurrentMeasurementNodes.containsKey(Host.currentHost().getName())) {
                        currentMeasurement = differentCurrentMeasurementNodes.get(Host.currentHost().getName());
                        differentCurrentMeasurementNodes.remove(Host.currentHost().getName());
                    }

                    System.out.println("BYZANTINE NODE " + id + " UPDATE MEASUREMENT: " + currentMeasurement);

                    //wait a bit before sending
                    try {
                        sleep(500);
                    } catch (HostFailureException e) {
                        System.err.println("BYZANTINE " + id + " host failed!!");
                        return;
                    }

                    if (currentLeader.equals(Host.currentHost().getName())) {
                        FinalDataResultTask finalDataResultTask = new FinalDataResultTask();
                        finalDataResultTask.setResult(currentMeasurement);
                        finalDataResultTask.setOriginHost(Host.currentHost().getName());
                        finalDataResultTask.setDestinationHost(baseStationHostName);

                        if (differentValueSentToBaseStation > 0) {
                            System.out.println("BYZANTINE NODE " + id + " SENDING " + differentValueSentToBaseStation + " TO BASE STATION");
                            finalDataResultTask.setResult(differentValueSentToBaseStation);
                            currentMeasurement = differentValueSentToBaseStation;
                            differentValueSentToBaseStation = 0.0f;
                        }
                        timeoutSendWithRetries(finalDataResultTask, baseStationHostName);
                    } else {
                        differentValueSentToBaseStation = 0.0f;
                    }
                }
                else {
                    System.out.println("BYZANTINE NODE " + id + " DID NOT RECEIVE ANY MEASUREMENT RESULTS");
                }

                measurementFloodResultStartTime = -1;
                measurementFloodStartTime = -1;
                measurementResults.clear();
                measurementFinalResults.clear();
                computedMeasurement = -1.0f;

                if (task == null || task instanceof DataResultTask)
                    continue;
            }
            if (trySendingToBaseStationNodes.containsKey(Host.currentHost().getName()) && trySendingToBaseStationCount > 0 &&
                trySendingToBaseStationLastAttemptTimestamp > 0 && System.currentTimeMillis() - trySendingToBaseStationLastAttemptTimestamp > trySendingToBaseStationDelay) {
                trySendingToBaseStationCount--;
                trySendingToBaseStationLastAttemptTimestamp = System.currentTimeMillis();

                FinalDataResultTask finalDataResultTask = new FinalDataResultTask();
                finalDataResultTask.setResult(trySendingToBaseStationNodes.get(Host.currentHost().getName()));
                finalDataResultTask.setOriginHost(Host.currentHost().getName());
                finalDataResultTask.setDestinationHost(baseStationHostName);
                timeoutSendWithRetries(finalDataResultTask, baseStationHostName);
                System.out.println("BYZANTINE NODE " + id + " TRIES SENDING RESULT TO BASE STATION WITHOUT DECISION PROCESS");
            }

            if (task != null) {

                //Received FinishSimulationTask from base station
                if (task instanceof FinishSimulationTask) {
                    break;
                }

                if (task instanceof ActivationTask) {
                    ActivationTask activationTask = (ActivationTask)task;

                    if (activationTask.getOriginHost().equals(baseStationHostName)) {
                        ranks.putAll(activationTask.getRanks());
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED ACTIVATION TASK FROM BASE STATION WITH THESE RANKS: " + ranks);
                    }
                }

                if (task instanceof LeaderSelectionTask) {
                    LeaderSelectionTask leaderSelectionTask = (LeaderSelectionTask) task;
                    boolean valid = false;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (leaderSelectionTask.getOriginHost().equals(baseStationHostName)) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER SELECTION TASK FROM BASE STATION");
                        valid = true;
                    }

                    //Check if the message came from the current leader
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (currentLeader != null && leaderSelectionTask.getOriginHost().equals(currentLeader)) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER SELECTION TASK FROM CURRENT LEADER: " + currentLeader);
                        valid = true;
                    }

                    if (ignoreLeaderSelectionNodes.contains(Host.currentHost().getName())) {
                        System.out.println("BYZANTINE NODE " + id + " IGNORES LEADER SELECTION");
                        ignoreLeaderSelectionNodes.remove(Host.currentHost().getName());
                        valid = false;
                    }

                    //Most likely received from byzantine node
                    if (!valid) {
                        differentValueSentToBaseStation = 0.0f;
                        continue;
                    }

                    //Wait a bit for all nodes to receive the leader selection message
                    try {
                        sleep(600 * NODE_COUNT);
                    } catch (HostFailureException e) {
                        System.err.println("Byzantine node " + id + "host failed!!");
                        return;
                    }

                    //This is a valid leader selection, setup the start time if there isn't an ongoing leader selection
                    if (leadershipSelectionApplicationStartTimestamp < 0) {
                        leadershipSelectionApplicationStartTimestamp = System.currentTimeMillis();
                    }

                    //Also setup the start time if something happened and the previous leader selection was not ended for this node
                    //Use 3 times the timeout, once for application, once for decision process, once for result flooding
                    if (System.currentTimeMillis() - leadershipSelectionApplicationStartTimestamp > 3 * LEADER_ELECTION_TIMEOUT) {
                        System.out.println("BYZANTINE NODE " + id +" RESTARTED LEADER SELECTION PROCESS");

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
                            ( (!currentLeader.equals(Host.currentHost().getName()) || currentLeaderEnrollsAgain) && !leaderApplicationSent)) {
                        leaderApplicationSent = true;

                        if (currentLeader != null) {
                            leaderApplicationSent = true;
                        }

                        if (differentMessagesAtLeaderApplicationNodes.containsKey(Host.currentHost().getName())) {
                            System.out.println("BYZANTINE NODE " + id + " WILL SEND A DIFFERENT MESSAGE TYPE");
                            Class differentMessageClass = differentMessagesAtLeaderApplicationNodes.get(Host.currentHost().getName());
                            try {
                                for (String destination : ranks.keySet()) {
                                    if (!destination.equals(Host.currentHost().getName())) {
                                        SimpleTask differentMessage = (SimpleTask)differentMessageClass.getDeclaredConstructor().newInstance();
                                        differentMessage.setOriginHost(Host.currentHost().getName());
                                        differentMessage.setDestinationHost(destination);
                                        timeoutSendWithRetries(differentMessage, destination);
                                    }
                                }

                            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                                e.printStackTrace();
                            }

                            differentMessagesAtLeaderApplicationNodes.remove(Host.currentHost().getName());
                        }
                        else {
                            //Flood with leadership applications
                            for (String destination : ranks.keySet()) {
                                //Don't send to localhost
                                if (!Host.currentHost().getName().equals(destination)) {
                                    if (!destination.equals(Host.currentHost().getName())) {
                                        LeadershipApplicationTask leadershipApplicationTask = new LeadershipApplicationTask();
                                        leadershipApplicationTask.setRank(ranks.get(Host.currentHost().getName()));
                                        leadershipApplicationTask.setOriginHost(Host.currentHost().getName());
                                        leadershipApplicationTask.setDestinationHost(destination);

                                        if (differentRanksNodes.containsKey(Host.currentHost().getName())) {
                                            leadershipApplicationTask.setRank(differentRanksNodes.get(Host.currentHost().getName()));
                                        }

                                        timeoutSendWithRetries(leadershipApplicationTask, destination);
                                    }
                                }
                            }
                        }

                        //Add myself in the list of applications
                        if (differentRanksNodes.containsKey(Host.currentHost().getName())) {
                            leadershipApplications.put(Host.currentHost().getName(), differentRanksNodes.get(Host.currentHost().getName()));
                            differentRanksNodes.remove(Host.currentHost().getName());
                        }
                        else {
                            leadershipApplications.put(Host.currentHost().getName(), ranks.get(Host.currentHost().getName()));
                        }
                    }

                }

                if (task instanceof LeadershipApplicationTask) {
                    LeadershipApplicationTask leadershipApplicationTask = (LeadershipApplicationTask)task;

                    //Received leadership application without a leadership selection triggered
                    if (leadershipSelectionApplicationStartTimestamp < 0)
                        continue;

                    //Check if the rank received in the application is bigger than the one in memory
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (ranks.get(leadershipApplicationTask.getOriginHost()) < leadershipApplicationTask.getRank()) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER APPLICATION TASK FROM " +
                                leadershipApplicationTask.getOriginHost() + " BUT RANK IS HIGHER THAN EXPECTED");
                        continue;
                    }

                    //Check if the rank received in the application is smaller than the one in memory and update it in memory
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (ranks.get(leadershipApplicationTask.getOriginHost()) > leadershipApplicationTask.getRank()) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER APPLICATION TASK FROM " +
                                leadershipApplicationTask.getOriginHost() + " BUT RANK IS SMALLER THAN EXPECTED AND UPDATED");
                        ranks.put(leadershipApplicationTask.getOriginHost(), leadershipApplicationTask.getRank());
                    }

                    //Check if the application did not come from the current leader
                    if (currentLeader != null && currentLeader.equals(leadershipApplicationTask.getOriginHost()))
                        continue;

                    //Save the application
                    leadershipApplications.put(leadershipApplicationTask.getOriginHost(), leadershipApplicationTask.getRank());

                    System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER APPLICATION TASK FROM " +
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

                        System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER RESULT TASK FROM " +
                                leaderResultTask.getOriginHost());
                    }
                    else {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED DUPLICATE LEADER RESULT TASK FROM " +
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

                    //Received measurement task during leader selection process or measurement process
                    if (measurementOrElectionInProgress()) {
                        valid = false;
                    }

                    if (ignoreDataMeasurementNodes.contains(Host.currentHost().getName())) {
                        System.out.println("BYZANTINE NODE " + id + " IGNORED MEASUREMENT TRIGGER ");
                        ignoreDataMeasurementNodes.remove(Host.currentHost().getName());
                        valid = false;
                    }

                    if (!valid)
                        continue;

                    System.out.println("BYZANTINE NODE " + id + " RECEIVED MEASUREMENT TRIGGER TASK FROM " +
                            triggerDataCollectionTask.getOriginHost());


                    //Ignore this request if it is too frequent
                    if (lastMeasurementTriggerTime == -1 || System.currentTimeMillis() - lastMeasurementTriggerTime >   - 1000)
                        lastMeasurementTriggerTime = System.currentTimeMillis();
                    else
                        continue;

                    measurementFloodStartTime = System.currentTimeMillis();
                    measurementResults.clear();
                    measurementFinalResults.clear();
                    computedMeasurement = -1.0f;

                    //Wait a bit for all nodes to receive the trigger message
                    try {
                        sleep(600 * NODE_COUNT);
                    } catch (HostFailureException e) {
                        System.err.println("BYZANTINE" + id + " host failed!!");
                        return;
                    }

                    //Generate a random value within bounds
                    int measurement = generateMeasurement();

                    if (differentDataMeasurementNodes.containsKey(Host.currentHost().getName())) {
                        measurement = differentDataMeasurementNodes.get(Host.currentHost().getName());
                        differentDataMeasurementNodes.remove(Host.currentHost().getName());
                    }

                    if (differentMessagesAtDataMeasurementNodes.containsKey(Host.currentHost().getName())) {
                        System.out.println("BYZANTINE NODE " + id + " WILL SEND A DIFFERENT MESSAGE TYPE");
                        Class differentMessageClass = differentMessagesAtDataMeasurementNodes.get(Host.currentHost().getName());
                        try {
                            for (String destination : ranks.keySet()) {
                                if (!destination.equals(Host.currentHost().getName())) {
                                    SimpleTask differentMessage = (SimpleTask)differentMessageClass.getDeclaredConstructor().newInstance();
                                    differentMessage.setOriginHost(Host.currentHost().getName());
                                    differentMessage.setDestinationHost(destination);
                                    timeoutSendWithRetries(differentMessage, destination);
                                }
                            }

                        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                            e.printStackTrace();
                        }

                        differentMessagesAtDataMeasurementNodes.remove(Host.currentHost().getName());
                    }
                    else {
                        //Flood with measurements
                        for (String destination : ranks.keySet()) {
                            if (!destination.equals(Host.currentHost().getName())) {
                                DataMeasurementTask dataMeasurementTask = new DataMeasurementTask();
                                dataMeasurementTask.setResult(measurement);
                                dataMeasurementTask.setOriginHost(Host.currentHost().getName());
                                dataMeasurementTask.setDestinationHost(destination);
                                timeoutSendWithRetries(dataMeasurementTask, destination);
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

                        System.out.println("BYZANTINE NODE " + id + " RECEIVED MEASUREMENT TASK FROM " +
                                dataMeasurementTask.getOriginHost());
                    }
                    else {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED DUPLICATE MEASUREMENT TASK FROM " +
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

                        System.out.println("BYZANTINE NODE " + id + " RECEIVED MEASUREMENT RESULT TASK FROM " +
                                dataResultTask.getOriginHost());
                    }
                    else {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED DUPLICATE MEASUREMENT RESULT TASK FROM " +
                                dataResultTask.getOriginHost());
                    }
                }

                if (task instanceof DataAckTask) {
                    DataAckTask dataAckTask = (DataAckTask) task;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (dataAckTask.getOriginHost().equals(baseStationHostName)) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED DATA ACK TASK FROM BASE STATION");
                    }
                    else {
                        continue;
                    }

                    if (!dataAckTask.getLeader().equals(currentLeader) || Math.abs(dataAckTask.getResult() - currentMeasurement) > 0.1) {
                        DataDisputeTask dataDisputeTask = new DataDisputeTask();
                        dataDisputeTask.setResult(currentMeasurement);
                        dataDisputeTask.setLeader(currentLeader);
                        dataDisputeTask.setOriginHost(Host.currentHost().getName());
                        dataDisputeTask.setDestinationHost(baseStationHostName);
                        timeoutSendWithRetries(dataDisputeTask, baseStationHostName);
                    }
                }

                if (task instanceof ReadjustmentTask) {
                    ReadjustmentTask readjustmentTask = (ReadjustmentTask) task;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (readjustmentTask.getOriginHost().equals(baseStationHostName)) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED READJUSTMENT TASK FROM BASE STATION" +
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

        System.out.println("BYZANTINE NODE " + id + " FINISHED");
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

    private void timeoutSendWithRetries(Task task, String destination) {
        boolean sent = false;
        long start = System.currentTimeMillis();

        try {
            if (Host.getByName(destination) == null || !Host.getByName(destination).isOn())
                return;
        } catch (HostNotFoundException ignored) { }

        while (!sent && System.currentTimeMillis() - start < 8000) {
            try {
                task.send(destination);
                sent = true;
            } catch (TransferFailureException | HostFailureException | TimeoutException ignored) { }
        }
    }
}
