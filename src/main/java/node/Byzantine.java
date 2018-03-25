package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.ActivationTask;
import task.FinishSimulationTask;
import task.LeaderSelectionTask;

import java.util.HashMap;

public class Byzantine extends Process {

    //In millis
    private static final long LEADER_ELECTION_TIMEOUT = 5000;

    //In seconds
    private static final double RECEIVE_TIMEOUT = 0.1;

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

        //The currently elected leader
        String currentLeader = null;

        //Internal ranks of other nodes
        HashMap<String, Integer> ranks = null;

        //Current applications held in memory
        HashMap<String, Integer> leadershipApplications = new HashMap<>();

        //The timestamp when the initial flood with applications began
        long leadershipSelectionApplicationStartTimestamp = -1;

        //If the current node flooded with his application
        boolean leaderApplicationSent = false;

        System.out.println("BYZANTINE NODE " + id + " STARTED");

        while (true) {

            Task task = null;
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
                System.out.println("BYZANTINE NODE " + id + " FINISHED RECEIVING LEADER APPLICATIONS");

                leadershipSelectionApplicationStartTimestamp = -1;
                leaderApplicationSent = false;

                //#TODO TRIGGER NEXT PHASE (DECIDE THE NEW LEADER)
                continue;
            }

            if (task != null) {

                //Received FinishSimulationTask from controller
                if (task instanceof FinishSimulationTask) {
                    break;
                }

                if (task instanceof ActivationTask) {
                    ActivationTask activationTask = (ActivationTask)task;

                    if (activationTask.getSource().getName().equals(baseStationHostName)) {
                        ranks = activationTask.getRanks();
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED ACTIVATION TASK FROM BASE STATION");
                    }
                }

                if (task instanceof LeaderSelectionTask) {
                    LeaderSelectionTask leaderSelectionTask = (LeaderSelectionTask) task;
                    boolean valid = false;

                    //Check if the message came from the base station
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (leaderSelectionTask.getSource().getName().equals(baseStationHostName)) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER SELECTION TASK FROM BASE STATION");
                        valid = true;
                    }

                    //Check if the message came from the current leader
                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (currentLeader != null && leaderSelectionTask.getSource().getName().equals(currentLeader)) {
                        System.out.println("BYZANTINE NODE " + id + " RECEIVED LEADER SELECTION TASK FROM CURRENT LEADER: " + currentLeader);
                        valid = true;
                    }

                    //Most likely received from byzantine node
                    if (!valid)
                        continue;

                    //This is a valid leader selection, setup the start time if there isn't an ongoing leader selection
                    //Also setup the start time if something happened and the previous leader selection was not ended for this node
                    //Use 3 times the timeout, once for application, once for decision process, once for result flooding
                    if (leadershipSelectionApplicationStartTimestamp < 0 ||
                            System.currentTimeMillis() - leadershipSelectionApplicationStartTimestamp > 3 * LEADER_ELECTION_TIMEOUT)
                        leadershipSelectionApplicationStartTimestamp = System.currentTimeMillis();

                    //Flood with applications only if not the current leader and did not flood already
                    if (currentLeader == null ||
                            (!currentLeader.equals(Host.currentHost().getName()) && !leaderApplicationSent)) {
                        leaderApplicationSent = true;
                        //#TODO FLOOD
                    }

                }

            }
        }

        System.out.println("BYZANTINE NODE " + id + " FINISHED");
    }
}
