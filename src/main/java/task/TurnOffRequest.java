package task;

import org.simgrid.msg.Task;

public class TurnOffRequest extends Task {

    private String name;

    public TurnOffRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
