package node;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;

public class SimulationController extends Process {

    public SimulationController(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 1)
            Msg.info("Wrong number of arguments for SimulationController node");
        int id = Integer.parseInt(args[0]);
        System.out.println("SimulationController NODE " + id + " STARTED");
    }
}