package node;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import task.FinishSimulationTask;
import task.MultiDestinationTask;

public class SimulationController extends Process {

    private static final double COMPUTE_SIZE = 0.0d;
    private static final double COMMUNICATION_SIZE = 0.0d;

    public SimulationController(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 1)
            Msg.info("Wrong number of arguments for SimulationController node");
        int id = Integer.parseInt(args[0]);
        System.out.println("SimulationController NODE " + id + " STARTED");

        //Insert some tasks into the network
        MultiDestinationTask task1 = new MultiDestinationTask("TASK1", COMPUTE_SIZE, COMMUNICATION_SIZE);
        task1.setFinalDestination("node_8");
        task1.addNextDestination("node_3");
        task1.addNextDestination("node_6");
        task1.addNextDestination("node_8");

        MultiDestinationTask task2 = new MultiDestinationTask("TASK1", COMPUTE_SIZE, COMMUNICATION_SIZE);
        task2.setFinalDestination("node_8");
        task2.addNextDestination("node_2");
        task2.addNextDestination("node_6");
        task2.addNextDestination("node_4");
        task2.addNextDestination("node_8");

        try {
            task1.send("node_1");
            task2.send("node_1");
        } catch (TransferFailureException e) {
            e.printStackTrace();
        } catch (HostFailureException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

        try {
            waitFor(3);
        } catch (HostFailureException e) {
            System.err.println("SimulationController host failed!!");
        }

        for (int i = 0; i < 10; i++) {
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

        System.out.println("SIMULATION CONTROLLER FINISHED EXECUTION");
    }
}