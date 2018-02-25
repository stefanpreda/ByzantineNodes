package driver;

import generator.ConfigurationGenerator;

public class Main {

    public static void main(String[] args) {
        ConfigurationGenerator generator = new ConfigurationGenerator();

        generator.generateTopology(10, 3);
        generator.generateNodeRoles(7, 3);
    }
}
