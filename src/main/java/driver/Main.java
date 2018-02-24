package driver;

import generator.ConfigurationGenerator;

public class Main {

    public static void main(String[] args) {
        ConfigurationGenerator generator = new ConfigurationGenerator();

        generator.generateNodeRoles(7, 3);
    }
}
