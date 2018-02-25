package node;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;

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
    }
}
