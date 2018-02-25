package node;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;

public class Legit extends Process {

    public Legit(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) {
        if (args.length != 1)
            Msg.info("Wrong number of arguments for Legit node");
        int id = Integer.parseInt(args[0]);
        System.out.println("LEGIT NODE " + id + " STARTED");
    }
}
