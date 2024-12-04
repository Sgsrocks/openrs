package net.openrs.cache.tools.definition_dumpers.npcs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NpcDefDumper {
	
	public static final String EXPORT = "E:/dump/export/NpcDefinitions.xml";
	
	public static final String LISTFILE = "E:/dump/types/npcs.txt";
	
	static String readFile(String path, Charset encoding) 
	  throws IOException 
	{
	  byte[] encoded = Files.readAllBytes(Paths.get(path));
	  return new String(encoded, encoding);
	}
	
	static void addAttr(Document doc, Element el, String key, String value) {
		Element attr = doc.createElement(key);
		attr.appendChild(doc.createTextNode(value));
		el.appendChild(attr);
	}

	public static void main(String[] args) throws Exception {
		
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		Document doc = docBuilder.newDocument();
		Element defList = doc.createElement("list");
		doc.appendChild(defList);

		int id = -1;
		String name = "null";
		int walkAnimation = 65535;
		int standAnimation = 65535;
		int turn180Animation = 65535;
		int turn90CWAnimation = 65535;
		int turn90CCWAnimation = 65535;
		int level = -1;
		int size = 1;
		boolean attackable = false;
		
		try (BufferedReader br = new BufferedReader(new FileReader(LISTFILE))) {
		    String line;
		    while ((line = br.readLine()) != null) {
			    if(line.startsWith("case ")) {
			    	if(id != -1) {
			    		Element el = doc.createElement("NpcDefinition");
			    		defList.appendChild(el);			    		
			    		addAttr(doc, el, "id", Integer.toString(id));
			    		addAttr(doc, el, "name", name);
			    		addAttr(doc, el, "walkAnimation", Integer.toString(walkAnimation));
			    		addAttr(doc, el, "standAnimation", Integer.toString(standAnimation));
			    		addAttr(doc, el, "turn180Animation", Integer.toString(turn180Animation));
			    		addAttr(doc, el, "turn90CWAnimation", Integer.toString(turn90CWAnimation));
			    		addAttr(doc, el, "turn90CCWAnimation", Integer.toString(turn90CCWAnimation));
			    		addAttr(doc, el, "level", Integer.toString(level));
			    		addAttr(doc, el, "size", Integer.toString(size));
			    		addAttr(doc, el, "attackable", Boolean.toString(attackable));
			    	}
			    	name = "null";
					walkAnimation = 65535;
					standAnimation = 65535;
					turn180Animation = 65535;
					turn90CWAnimation = 65535;
					turn90CCWAnimation = 65535;
					level = -1;
					size = 1;
					attackable = false;
					line = line.replace("case ", "");
			    	line = line.replace(":", "");
			    	id = Integer.parseInt(line);
			    } else if(line.contains("type.name")) {
			    	line = line.replace("type.name = \"", "");
			    	line = line.replace("\";", "");
			    	if(line.contains("<col"))
			    		line = line.substring(13, line.length() - 6);
			    	name = line.trim();	
			    } else if(line.contains("type.stanceAnimation")) {
			    	line = line.replace("type.stanceAnimation = ", "");
			    	line = line.replace(";", "");
			    	standAnimation = Integer.parseInt(line.trim());	
			    } else if(line.contains("type.walkAnimation")) {
			    	line = line.replace("type.walkAnimation = ", "");
			    	line = line.replace(";", "");
			    	walkAnimation = Integer.parseInt(line.trim());	
			    } else if(line.contains("type.rotate180Animation")) {
			    	line = line.replace("type.rotate180Animation = ", "");
			    	line = line.replace(";", "");
			    	turn180Animation = Integer.parseInt(line.trim());	
			    } else if(line.contains("type.rotate90LeftAnimation")) {
			    	line = line.replace("type.rotate90LeftAnimation = ", "");
			    	line = line.replace(";", "");
			    	turn90CWAnimation = Integer.parseInt(line.trim());	
			    } else if(line.contains("type.rotate90RightAnimation")) {
			    	line = line.replace("type.rotate90RightAnimation = ", "");
			    	line = line.replace(";", "");
			    	turn90CCWAnimation = Integer.parseInt(line.trim());	
			    } else if(line.contains("type.combatLevel")) {
			    	line = line.replace("type.combatLevel = ", "");
			    	line = line.replace(";", "");
			    	level = Integer.parseInt(line.trim());	
			    } else if(line.contains("type.tileSpacesOccupied")) {
			    	line = line.replace("type.tileSpacesOccupied = ", "");
			    	line = line.replace(";", "");
			    	size = Integer.parseInt(line.trim());	
			    } else if(line.contains("type.options") && line.contains("Attack")) {
			    	attackable = true;	
			    }
			}
		}
		
		Element el = doc.createElement("NpcDefinition");
		defList.appendChild(el);			    		
		addAttr(doc, el, "id", Integer.toString(id));
		addAttr(doc, el, "name", name);
		addAttr(doc, el, "walkAnimation", Integer.toString(walkAnimation));
		addAttr(doc, el, "standAnimation", Integer.toString(standAnimation));
		addAttr(doc, el, "turn180Animation", Integer.toString(turn180Animation));
		addAttr(doc, el, "turn90CWAnimation", Integer.toString(turn90CWAnimation));
		addAttr(doc, el, "turn90CCWAnimation", Integer.toString(turn90CCWAnimation));
		addAttr(doc, el, "level", Integer.toString(level));
		addAttr(doc, el, "size", Integer.toString(size));
		addAttr(doc, el, "attackable", Boolean.toString(attackable));
		
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(new File(EXPORT));

		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		transformer.transform(source, result);		
	}
}
