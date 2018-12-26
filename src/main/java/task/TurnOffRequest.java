package task;

public class TurnOffRequest extends SimpleTask {

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
