package generator;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.Random;

public class ConfigurationGenerator {

    private Random randomNumberGenerator = new Random();
    private DocumentBuilder docBuilder = null;
    private Transformer transformer = null;

    private static final String PLATFORM_VERSION = "4.1";

    public static final String FOLDER_NAME = "generated";
    public static final String ROLES_FILE_NAME = "byzantine.xml";
    public static final String TOPOLOGY_FILE_NAME = "topology.xml";

    private static final String HOST_SPEED = "100.00Mf";
    private static final String LINK_LATENCY = "50.00us";
    private static final int LINK_BANDWIDTH_MIN = 50;
    private static final int LINK_BANDWIDTH_MAX = 100;

    //High value so Dijkstra always returns direct link as best link
    private static final String SIMULATION_NODE_LINK_BANDWIDTH = "200.00MBps";

    public ConfigurationGenerator() {

        try {

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docBuilder = docFactory.newDocumentBuilder();

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "http://simgrid.gforge.inria.fr/simgrid/simgrid.dtd");

        } catch (ParserConfigurationException e) {
            System.err.println(e.getLocalizedMessage());
            e.printStackTrace();
        } catch (TransformerException e) {
            System.err.println(e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public void generateNodeRoles(int countLegitNodes, int countByzantineNodes) {
        Document doc = docBuilder.newDocument();
        doc.setXmlStandalone(true);

        // Create tags <platform version=""></platform>
        Element platformElement = doc.createElement("platform");
        Attr platformAttr = doc.createAttribute("version");
        platformAttr.setValue(PLATFORM_VERSION);
        platformElement.setAttributeNode(platformAttr);

        //Append platform directly to doc as root element
        doc.appendChild(platformElement);

        //Create legitimate nodes
        for (int i = 0; i < countLegitNodes; i++) {

            // Create tags <actor host="" function=""></actor>
            Element actorLegitElement = doc.createElement("actor");
            Attr actorLegitAttr = doc.createAttribute("host");
            actorLegitAttr.setValue("node_" + i);
            actorLegitElement.setAttributeNode(actorLegitAttr);
            actorLegitAttr = doc.createAttribute("function");
            actorLegitAttr.setValue("node.Legit");
            actorLegitElement.setAttributeNode(actorLegitAttr);

            // Create tags <argument value=""/>
            Element argumentElement = doc.createElement("argument");
            Attr argumentAttr = doc.createAttribute("value");
            argumentAttr.setValue(String.valueOf(i));
            argumentElement.setAttributeNode(argumentAttr);

            // Append argument to actor
            actorLegitElement.appendChild(argumentElement);

            // Append actor to platform
            platformElement.appendChild(actorLegitElement);
        }

        //Create byzantine nodes
        for (int i = 0; i < countByzantineNodes; i++) {
            int index = countLegitNodes + i;

            // Create tags <actor host="" function=""></actor>
            Element actorLegitElement = doc.createElement("actor");
            Attr actorLegitAttr = doc.createAttribute("host");
            actorLegitAttr.setValue("node_" + index);
            actorLegitElement.setAttributeNode(actorLegitAttr);
            actorLegitAttr = doc.createAttribute("function");
            actorLegitAttr.setValue("node.Byzantine");
            actorLegitElement.setAttributeNode(actorLegitAttr);

            // Create tags <argument value=""/>
            Element argumentElement = doc.createElement("argument");
            Attr argumentAttr = doc.createAttribute("value");
            argumentAttr.setValue(String.valueOf(index));
            argumentElement.setAttributeNode(argumentAttr);

            // Append argument to actor
            actorLegitElement.appendChild(argumentElement);

            // Append actor to platform
            platformElement.appendChild(actorLegitElement);
        }

        // Create a SimulationController node
        int index = countLegitNodes + countByzantineNodes;
        // Create tags <actor host="" function=""></actor>
        Element actorLegitElement = doc.createElement("actor");
        Attr actorLegitAttr = doc.createAttribute("host");
        actorLegitAttr.setValue("node_" + index);
        actorLegitElement.setAttributeNode(actorLegitAttr);
        actorLegitAttr = doc.createAttribute("function");
        actorLegitAttr.setValue("node.SimulationController");
        actorLegitElement.setAttributeNode(actorLegitAttr);

        // Create tags <argument value=""/>
        Element argumentElement = doc.createElement("argument");
        Attr argumentAttr = doc.createAttribute("value");
        argumentAttr.setValue(String.valueOf(index));
        argumentElement.setAttributeNode(argumentAttr);

        // Append argument to actor
        actorLegitElement.appendChild(argumentElement);

        // Append actor to platform
        platformElement.appendChild(actorLegitElement);

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(FOLDER_NAME + "/" + ROLES_FILE_NAME));

        try {

            //Create folder if it doesn't exist
            File directory = new File(FOLDER_NAME);
            if (!directory.exists())
                directory.mkdir();

            //Output the configuration to file
            transformer.transform(source, result);

        } catch (TransformerException e) {
            System.err.println(e.getLocalizedMessage());
            e.printStackTrace();
        }

        System.out.println("FINISHED GENERATING CONFIGURATION FILE: " + FOLDER_NAME + "/" + ROLES_FILE_NAME);
    }

    //connLevel = node[i] connects to node[i+1]..node[i+level]
    public void generateTopology(int nodeCount, int connLevel) {
        int linkCount = connLevel * nodeCount;
        Document doc = docBuilder.newDocument();
        doc.setXmlStandalone(true);

        // Create tags <platform version=""></platform>
        Element platformElement = doc.createElement("platform");
        Attr platformAttr = doc.createAttribute("version");
        platformAttr.setValue(PLATFORM_VERSION);
        platformElement.setAttributeNode(platformAttr);

        //Append platform directly to doc as root element
        doc.appendChild(platformElement);

        // Create tags <zone id="" routing=""></zone>
        Element zoneElement = doc.createElement("zone");
        Attr zoneAttr = doc.createAttribute("id");
        zoneAttr.setValue("zone0");
        zoneElement.setAttributeNode(zoneAttr);
        zoneAttr = doc.createAttribute("routing");
        zoneAttr.setValue("Dijkstra");
        zoneElement.setAttributeNode(zoneAttr);

        //Append zone to platform
        platformElement.appendChild(zoneElement);

        //Generate nodes (1 extra for SimulationController)
        for (int i = 0; i < nodeCount + 1; i++) {

            //Create tags <host id="" speed=""></host>
            Element hostElement = doc.createElement("host");
            Attr hostAttr = doc.createAttribute("id");
            hostAttr.setValue("node_" + i);
            hostElement.setAttributeNode(hostAttr);
            hostAttr = doc.createAttribute("speed");
            hostAttr.setValue(HOST_SPEED);
            hostElement.setAttributeNode(hostAttr);

            //Append host to zone
            zoneElement.appendChild(hostElement);
        }

        //Generate links
        for (int i = 0; i < linkCount; i++) {

            //Create tags <link id="" bandwidth="" latency=""></link>
            Element linkElement = doc.createElement("link");
            Attr linkAttr = doc.createAttribute("id");
            linkAttr.setValue("link_" + i);
            linkElement.setAttributeNode(linkAttr);
            linkAttr = doc.createAttribute("bandwidth");
            linkAttr.setValue(String.valueOf(LINK_BANDWIDTH_MIN + randomNumberGenerator
                    .nextInt(LINK_BANDWIDTH_MAX - LINK_BANDWIDTH_MIN)) + ".00MBps");
            linkElement.setAttributeNode(linkAttr);
            linkAttr = doc.createAttribute("latency");
            linkAttr.setValue(LINK_LATENCY);
            linkElement.setAttributeNode(linkAttr);

            //Append link to zone
            zoneElement.appendChild(linkElement);
        }

        //Create one extra link which will be used for connecting the SimulationController
        //Create tags <link id="" bandwidth="" latency=""></link>
        Element linkElement = doc.createElement("link");
        Attr linkAttr = doc.createAttribute("id");
        linkAttr.setValue("link_" + linkCount);
        linkElement.setAttributeNode(linkAttr);
        linkAttr = doc.createAttribute("bandwidth");
        linkAttr.setValue(SIMULATION_NODE_LINK_BANDWIDTH);
        linkElement.setAttributeNode(linkAttr);
        linkAttr = doc.createAttribute("latency");
        linkAttr.setValue(LINK_LATENCY);
        linkElement.setAttributeNode(linkAttr);

        //Append link to zone
        zoneElement.appendChild(linkElement);

        //Generate 1-hop routes
        for (int i = 0; i < nodeCount; i++) {

            //j = count of links
            for (int j = 0; j < connLevel; j++) {

                int destNodeID = (i + j + 1) % nodeCount;
                int linkID = i * connLevel + j;

                //Route from i -> destNodeID
                //Create tags <route src="" dst=""></route>
                Element routeElement = doc.createElement("route");
                Attr routeAttr = doc.createAttribute("src");
                routeAttr.setValue("node_" + i);
                routeElement.setAttributeNode(routeAttr);
                routeAttr = doc.createAttribute("dst");
                routeAttr.setValue("node_" + destNodeID);
                routeElement.setAttributeNode(routeAttr);

                //Create tags <link_ctn id=""></link_ctn>
                Element linkCtnElement = doc.createElement("link_ctn");
                Attr linkCtnAttr = doc.createAttribute("id");
                linkCtnAttr.setValue("link_" + linkID);
                linkCtnElement.setAttributeNode(linkCtnAttr);

                //Append link_ctn to route
                routeElement.appendChild(linkCtnElement);

                //Append route to zone
                zoneElement.appendChild(routeElement);
            }
        }

        //Generate 1-hop routes for the SimulationController
        for (int i = 0; i < nodeCount; i++) {

            //Route from i -> nodeCount (last node)
            //Create tags <route src="" dst=""></route>
            Element routeElement = doc.createElement("route");
            Attr routeAttr = doc.createAttribute("src");
            routeAttr.setValue("node_" + i);
            routeElement.setAttributeNode(routeAttr);
            routeAttr = doc.createAttribute("dst");
            routeAttr.setValue("node_" + nodeCount);
            routeElement.setAttributeNode(routeAttr);

            //Create tags <link_ctn id=""></link_ctn>
            Element linkCtnElement = doc.createElement("link_ctn");
            Attr linkCtnAttr = doc.createAttribute("id");
            linkCtnAttr.setValue("link_" + linkCount);
            linkCtnElement.setAttributeNode(linkCtnAttr);

            //Append link_ctn to route
            routeElement.appendChild(linkCtnElement);

            //Append route to zone
            zoneElement.appendChild(routeElement);
        }

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(FOLDER_NAME + "/" + TOPOLOGY_FILE_NAME));

        try {

            //Create folder if it doesn't exist
            File directory = new File(FOLDER_NAME);
            if (!directory.exists())
                directory.mkdir();

            //Output the configuration to file
            transformer.transform(source, result);

        } catch (TransformerException e) {
            System.err.println(e.getLocalizedMessage());
            e.printStackTrace();
        }

        System.out.println("FINISHED GENERATING TOPOLOGY FILE: " + FOLDER_NAME + "/" + TOPOLOGY_FILE_NAME);
    }
}
