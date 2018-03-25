package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.*;

import java.util.HashMap;

public class Byzantine extends Process {

    //In millis #TODO MAYBE COMPUTE IT BASED ON THE NUMBER OF HOSTS
    private static final long LEADER_ELECTION_TIMEOUT = 15000;

    //In seconds
    private static final double RECEIVE_TIMEOUT = 0.5;

    //The currently elected leader
    private String currentLeader = null;

    //Internal ranks of other nodes
    private HashMap<String, Integer> ranks = new HashMap<>();

    //Current applications held in memory
    private HashMap<String, Integer> leadershipApplications = new HashMap<>();

    //Current results held in memory
    private HashMap<String, String> leadershipResults = new HashMap<>();

    //The timestamp when the initial flood with applications began
    private long leadershipSelectionApplicationStartTimestamp = -1;

    //The timestamp when the flood with results began
    private long leadershipSelectionResultStartTimestamp = -1;

    //If the current node flooded with his application
    private boolean leaderApplicationSent = false;

    //The leader determined by this host based on applications
    private String computedLeader = null;


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

        System.out.println("BYZANTINE NODE " + id + " STARTED");

        while (true) {

            Task task = null;

            //RECEIVE LOOP
            try {
                task = Task.receive(getHost().getName(), RECEIVE_TIMEOUT);
            } catch (TransferFailureException e) {
                System.err.println("Node " + id + " FAILED to transfer data\n" + e.getLocalizedMessage());
            } catch (HostFailureException e) {
                System.err.println("Node " + id + " FAILED to transfer data\n" + e.getLocalizedMessage());
            } catch (TimeoutException e) {

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

                if (computedLeader != null)
                    System.out.println("BYZANTINE NODE " + id + " COMPUTED " + computedLeader + " AS THE NEW LEADER");
                else
                    System.out.println("BYZANTINE NODE " + id + " DID NOT HAVE ANY LEADERSHIP APPLICATIONS");

                leadershipSelectionApplicationStartTimestamp = -1;
                leadershipApplications.clear();
                leaderApplicationSent = false;

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
                            leaderResultTask.isend(destination);
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
                    System.out.println("BYZANTINE NODE " + id + " UPDATE LEADER: " + newLeader);
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

                if (task == null || task instanceof LeaderResultTask)
                    continue;
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

                    //Most likely received from byzantine node
                    if (!valid)
                        continue;

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

                    //Flood with applications only if not the current leader and did not flood already
                    if (currentLeader == null ||
                            (!currentLeader.equals(Host.currentHost().getName()) && !leaderApplicationSent)) {
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
                                    leadershipApplicationTask.isend(destination);
                                }
                            }
                        }

                        //Add myself in the list of applications
                        leadershipApplications.put(Host.currentHost().getName(), ranks.get(Host.currentHost().getName()));
                    }

                }

                if (task instanceof LeadershipApplicationTask) {
                    LeadershipApplicationTask leadershipApplicationTask = (LeadershipApplicationTask)task;

                    //Check if the rank received in the application is bigger than the one in memory
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (ranks.get(leadershipApplicationTask.getOriginHost()) > leadershipApplicationTask.getRank()) {
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

                    //Save the application
                    leadershipApplications.put(leadershipApplicationTask.getOriginHost(), leadershipApplicationTask.getRank());

                    System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER APPLICATION TASK FROM " +
                            leadershipApplicationTask.getOriginHost());

                }

                if (task instanceof LeaderResultTask) {
                    LeaderResultTask leaderResultTask = (LeaderResultTask)task;

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

            }
        }

        System.out.println("BYZANTINE NODE " + id + " FINISHED");
    }
}
