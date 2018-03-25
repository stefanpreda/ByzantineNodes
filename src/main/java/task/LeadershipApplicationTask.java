package task;

import org.simgrid.msg.Task;

public class LeadershipApplicationTask extends Task {

    private int rank;

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

}
