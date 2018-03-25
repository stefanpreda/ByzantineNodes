package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.ActivationTask;
import task.FinishSimulationTask;

import java.util.HashMap;

public class Legit extends Process {

    public Legit(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 2)
            Msg.info("Wrong number of arguments for Legit node");
        int id = Integer.parseInt(args[0]);
        String baseStationHostName = args[1];
        HashMap<String, Integer> ranks = null;

        System.out.println("LEGIT NODE " + id + " STARTED");

        while (true) {

            Task task = null;
            try {
                task = Task.receive(getHost().getName());
            } catch (TransferFailureException e) {
                System.err.println("Node " + id + " FAILED to transfer data\n" + e.getLocalizedMessage());
            } catch (HostFailureException e) {
                System.err.println("Node " + id + " FAILED to transfer data\n" + e.getLocalizedMessage());
            } catch (TimeoutException e) {

            }

            if (task != null) {

                //Received FinishSimulationTask from controller
                if (task instanceof FinishSimulationTask) {
                    break;
                }

                if (task instanceof ActivationTask) {
                    ActivationTask activationTask = (ActivationTask)task;

                    //THE UNDERLYING ROUTING PROTOCOL CAN CONFIRM THE IDENTITY OF THE SOURCE
                    if (activationTask.getSource().getName().equals(baseStationHostName)) {
                        ranks = activationTask.getRanks();
                        System.out.println("LEGIT NODE " + id + " RECEIVED ACTIVATION TASK FROM BASE STATION");
                    }
                }

            }
        }

        System.out.println("LEGIT NODE " + id + " FINISHED");
    }
}
