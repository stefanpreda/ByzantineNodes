package generator;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

public class ConfigurationGenerator {

    private DocumentBuilder docBuilder = null;
    private Transformer transformer = null;
    private static final String FOLDER_NAME = "generated";
    private static final String ROLES_FILE_NAME = "byzantine.xml";
    private static final String PLATFORM_VERSION = "4.1";

    public ConfigurationGenerator() {

        try {

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docBuilder = docFactory.newDocumentBuilder();

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformer = transformerFactory.newTransformer();

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
            actorLegitAttr.setValue("legit_" + i);
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

            // Create tags <actor host="" function=""></actor>
            Element actorLegitElement = doc.createElement("actor");
            Attr actorLegitAttr = doc.createAttribute("host");
            actorLegitAttr.setValue("byzantine_" + i);
            actorLegitElement.setAttributeNode(actorLegitAttr);
            actorLegitAttr = doc.createAttribute("function");
            actorLegitAttr.setValue("node.Byzantine");
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
}
