package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.FinishSimulationTask;
import task.MultiDestinationTask;

public class Byzantine extends Process {

    public Byzantine(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 1)
            Msg.info("Wrong number of arguments for BYZANTINE node");
        int id = Integer.parseInt(args[0]);
        System.out.println("BYZANTINE NODE " + id + " STARTED");

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

                //Received MultiDestinationTask from controller, route it normally for now
                if (task instanceof MultiDestinationTask) {
                    MultiDestinationTask receivedTask = (MultiDestinationTask)task;
                    String finalDestination = receivedTask.getFinalDestination();
                    String nextDestination = receivedTask.getNextDestination();

                    if (getHost().getName().equals(finalDestination)) {
                        System.out.println("Node " + id + " RECEIVED data successfully and it is final destination");
                    }
                    else {
                        try {
                            task.send(nextDestination);
                        } catch (TransferFailureException e) {
                            e.printStackTrace();
                        } catch (HostFailureException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        }
                        System.out.println("Node " + id + " ROUTED data to " + nextDestination);
                    }
                }

            }
        }

        System.out.println("BYZANTINE NODE " + id + " FINISHED");
    }
}
