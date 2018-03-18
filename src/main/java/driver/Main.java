package driver;

import generator.ConfigurationGenerator;
import org.simgrid.msg.Msg;

public class Main {

    public static void main(String[] args) {
        ConfigurationGenerator generator = new ConfigurationGenerator();

        generator.generateTopology(10, 3, false);
        generator.generateNodeRoles(7, 3);

        Msg.init(args);

        Msg.createEnvironment(ConfigurationGenerator.FOLDER_NAME + "/" + ConfigurationGenerator.TOPOLOGY_FILE_NAME);
        Msg.deployApplication(ConfigurationGenerator.FOLDER_NAME + "/" + ConfigurationGenerator.ROLES_FILE_NAME);

        Msg.run();
    }
}
