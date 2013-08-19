package wsdl_compare;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.net.URL;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

public class Validate {
	


	public String execute(String resultDir, String bankUrl, Map<String,String> excelsysWsdlMap, Date date1)
    {
    	boolean dirExists = true;
    	StringBuilder message = new StringBuilder();
    	String msg = new String();
    	if(!resultDir.endsWith("/"))
    		resultDir += "/";
    	File dir = new File(resultDir);
    	if (!dir.exists()){
    		dirExists = dir.mkdir();
    		if(!dirExists) {
    			msg = "No se pudo crear el directorio '" + resultDir + "'. Las diferencias se esribiran en la consola. ";
    			message.append(msg + "\n");
    			System.out.println(msg);
    		}
    	}
    	if(!bankUrl.endsWith("/"))
    		bankUrl += "/";
    	Date date2 = new Date();
    	long diff = (date2.getTime() - date1.getTime()) / 1000;
    	msg = "Se encontraron " + excelsysWsdlMap.size() + " archivos wsdl de excelsys. " +  diff + " segundos transcurridos hasta aqui. ";
    	message.append(msg + "\n");
		System.out.println(msg);
        
		msg = "Buscando WSDLs del banco en la ruta '" + bankUrl + "'";
		message.append(msg + " \n");
		System.out.println(msg);
		
    	Map<String, String> bankWsdlMap = getBankWsdls(bankUrl, excelsysWsdlMap);
    	
    	if(bankWsdlMap != null) {
    		
    		//countElementsInWsdls(excelsysWsdlMap, bankWsdlMap);
    		
	    	date2 = new Date();
	    	diff = (date2.getTime() - date1.getTime()) / 1000;
	    	msg = "Se encontraron " + bankWsdlMap.size() + " wsdl del banco. " +  diff + " segundos transcurridos hasta aqui. ";
	    	message.append(msg + "\n");
			System.out.println(msg);
			
	    	//writeWsdls(bankWsdlMap, resultDir + "bank_effective_wsdls");
	    	
	        List<String> missingWsdls = findMissingWsdls(bankWsdlMap, excelsysWsdlMap);
	    	date2 = new Date();
	    	diff = (date2.getTime() - date1.getTime()) / 1000;
	    	msg = "No se encontraron " + missingWsdls.size() + " wsdl en el banco. " +  diff + " segundos transcurridos hasta aqui. ";
	    	message.append(msg + "\n");
			System.out.println(msg);
	        
	        List<String> foundWsdls  = listFoundWsdls(bankWsdlMap);
	        
	        Map<String,String> elementsToCompareMap = new HashMap<String,String>();

	        elementsToCompareMap.put("operation", "name");
	        elementsToCompareMap.put("service", "name");
	        elementsToCompareMap.put("port", "name");
	        elementsToCompareMap.put("portType", "name");
	        elementsToCompareMap.put("complexType", "name");
	        elementsToCompareMap.put("address", "location");
	        elementsToCompareMap.put("element", "name");
	        elementsToCompareMap.put("choice", "name");
	        
	        Map<String, List<String>> wsdlsWithDifferences = getDifferencesOnMaps(excelsysWsdlMap,	bankWsdlMap, elementsToCompareMap);
	        
	    	date2 = new Date();
	    	diff = (date2.getTime() - date1.getTime()) / 1000;

        	msg = "Proceso terminado en " +  diff + " segundos. \n\n";
			System.out.println(msg);
        	message.append(msg);

        	writeReportInAFile(foundWsdls, wsdlsWithDifferences, missingWsdls, resultDir, dirExists);

	        if (!wsdlsWithDifferences.containsKey("contentDifferences") && 
	        	!wsdlsWithDifferences.containsKey("occursDifferences") &&  
	        	missingWsdls.size() == 0) 
	        {
	        	msg = "Todos los WSDL son iguales.";
				System.out.println(msg);
	        	message.append(msg);
	        	return(message.toString());
	        }
	        else {
	        	if(dirExists){
		        	//String minDifDir = resultDir + "minoccurs_differences/";
	        		//writeMinOccursDiffInFiles(wsdlsWithDifferences, minDifDir);
		        	msg = "Se encontraron diferencias en los WSDL.\nEstas diferencias estan compendiadas en el archivo: '" 
		        			+ resultDir + "reporte.txt'.";
					System.out.println(msg);
		        	message.append(msg + "\n");
	        		return(message.toString());
	        	}
	        	else
	        	{
	        		msg = "Se encontraron diferencias en los WSDL.\nComo no se pudo acceder ni crear el directorio '" 
	        				+ resultDir + "' Las diferencias se escribieron en la consola.";
					System.out.println(msg);
	        		message.append(msg + "\n");
	        		return(message.toString());
	        	}
	        }
    	}
    	else {
    		msg = "No se puede acceder a la URL: " + bankUrl;
			System.out.println(msg);
    		message.append(msg + "\n");
    		return(message.toString());
    	}
    }
   
	protected Map<String, List<String>> getDifferencesOnMaps(Map<String, String> excelsysWsdlMap, Map<String, String> bankWsdlMap, Map<String, String> elementsToFind)
	{
		Map<String,List<String>> differencesMap = new HashMap<String,List<String>>();
		Map<String,List<String>> returnMap = new HashMap<String,List<String>>();
		
		SortedSet<String> wsdlList = new TreeSet<String>(bankWsdlMap.keySet());
		for(String wsdlName: wsdlList) {
			try {
				String bankXML = bankWsdlMap.get(wsdlName);
				if (!bankXML.isEmpty()) {
					Document bankDoc = loadXMLFromString(bankXML);
					Document excelsysDoc = loadXMLFromString(excelsysWsdlMap.get(wsdlName));
					Iterator<Entry<String, String>> elementIterator = elementsToFind.entrySet().iterator();
					System.out.println("Buscando diferencias en " + wsdlName);
					boolean noDiff = true;
					while(elementIterator.hasNext()){
						String elementKey = elementIterator.next().getKey();
						returnMap = getDifferencesOnDocs(wsdlName, bankDoc, excelsysDoc, elementKey, elementsToFind.get(elementKey));
						if(returnMap.get("contentDifferences").size() != 0 || returnMap.get("occursDifferences").size() != 0) {
							noDiff = false;
							if(differencesMap.containsKey("contentDifferences")){
								differencesMap.get("contentDifferences").addAll(returnMap.get("contentDifferences"));
							}
							else {
								differencesMap.put("contentDifferences",returnMap.get("contentDifferences"));
							}
							if(differencesMap.containsKey("occursDifferences")){
								differencesMap.get("occursDifferences").addAll(returnMap.get("occursDifferences"));
							}
							else {
								differencesMap.put("occursDifferences",returnMap.get("occursDifferences"));
							}
						}
					}
					if(noDiff) {
						List<String> noDifferences = new ArrayList<String>();
						noDifferences.add(wsdlName);
						if(differencesMap.containsKey("noDifferences")){
							differencesMap.get("noDifferences").addAll(noDifferences);
						}
						else {
							differencesMap.put("noDifferences",noDifferences);
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("Finalizada la busqueda de diferencias.");
		return differencesMap;
	}
	   
	protected List<String> listFoundWsdls(Map<String, String> bankWsdlMap) {
		List<String> foundWsdls = new ArrayList<String>();
		
		SortedSet<String> wsdlList = new TreeSet<String>(bankWsdlMap.keySet());
		for(String wsdlName: wsdlList) {
       		foundWsdls.add(wsdlName);
        }
		return foundWsdls;
	}
	
	protected Map<String, Integer> countElementsInWsdls(Map<String, String> excelsysWsdlMap, Map<String, String> bankWsdlMap){

		Map<String,Integer> countElements = new HashMap<String,Integer>();
		String expression = "//*[local-name()='element'][@name]";
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();		
		SortedSet<String> wsdlList = new TreeSet<String>(bankWsdlMap.keySet());
		for(String wsdlName: wsdlList) {
			String bankXML = excelsysWsdlMap.get(wsdlName);
			if (!bankXML.isEmpty()) {
				try {
						Document bankDoc = loadXMLFromString(bankXML);
						NodeList bankNodeList = (NodeList) xpath.evaluate(expression, bankDoc, XPathConstants.NODESET);
						List<String> nameList = new ArrayList<String>();
						for(int i=0; i<bankNodeList.getLength(); i++) {
							Element element = (Element) bankNodeList.item(i);
							String nameElement = element.getAttribute("name");
							if(!nameList.contains(nameElement)){
								nameList.add(nameElement);
							}
						}
						System.out.println(wsdlName +  ":" + nameList.size());
						countElements.put(wsdlName, bankNodeList.getLength());
				} catch (XPathExpressionException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
        }
		return countElements;
	}
	
   protected Map<String, List<String>> getDifferencesOnDocs(String wsdlName, Document bankDoc, Document excelsysDoc, String tagName, String attributeName) 
   {
		List<String> contentDifferences = new ArrayList<String>();
		List<String> occursDifferences = new ArrayList<String>();
		List<String> reported = new ArrayList<String>();
		Map<String,List<String>> differencesMap = new HashMap<String,List<String>>();
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		String expression = "//*[local-name()='" + tagName + "']";
		try {
			
			NodeList bankNodeList = (NodeList) xpath.evaluate(expression, bankDoc, XPathConstants.NODESET);
			NodeList excelsysNodeList = (NodeList) xpath.evaluate(expression, excelsysDoc, XPathConstants.NODESET);
			
			if(tagName.compareTo("choice") == 0){
				contentDifferences.addAll(findChoices(wsdlName, bankNodeList, excelsysDoc));
			}
			
			if(tagName.compareTo("element") == 0){
				occursDifferences.addAll(findMinOccursDifferences(wsdlName, bankNodeList, excelsysNodeList));
			}
			
			for (int i = 0; i < bankNodeList.getLength(); i++) {
				Node nodo = bankNodeList.item(i).getAttributes().getNamedItem(attributeName);
				if(nodo != null) {
					String bankElement = nodo.getNodeValue();
					if(!findElementInListByAttribute(bankElement, excelsysNodeList, attributeName)) {
						if(!reported.contains(bankElement)) {
							reported.add(bankElement);
							StringBuilder str = new StringBuilder();
							str.append("En el wsdl de Excelsys '"+ wsdlName.replaceFirst("-", "/") + "' no existe '" + tagName + "' con atributo " + attributeName + "='" + bankElement + "'. ");
							str.append("Se encontraron estos:");
							for (int j = 0; j < excelsysNodeList.getLength(); j++) {
								Element node = (Element) excelsysNodeList.item(j);
								if(!node.getAttribute(attributeName).isEmpty())
									str.append(" '" + node.getAttribute(attributeName) + "'");
							}
							contentDifferences.add(str.toString());
						}
					}
				}
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		differencesMap.put("contentDifferences", contentDifferences);
		differencesMap.put("occursDifferences", occursDifferences);
		return differencesMap;
   }
   

   protected List<String> findChoices(String wsdlName, NodeList bankChoiceNodeList, Document excelsysDoc) {
	   List<String> differences = new ArrayList<String>();
	   String attributeRef = "ref";
	   String attributeName = "name";
	   //Por cada Choice encontrado en el Banco
	   for (int i = 0; i < bankChoiceNodeList.getLength(); i++) {
			Element bankChoiceElement = (Element) bankChoiceNodeList.item(i);
			Element bankChoiceParent = (Element) bankChoiceElement.getParentNode();
			boolean stopCondition = false;
			boolean foundParent = false;
			while(!stopCondition && !foundParent){
				if(bankChoiceParent.getAttributeNode(attributeName) == null){
					bankChoiceParent = (Element) bankChoiceParent.getParentNode();
					if(bankChoiceParent == null){
						stopCondition = true;
					}
				}
				else{
					foundParent = true;
				}
			}
			if(foundParent) {
				String bankChoiceParentName = bankChoiceParent.getAttribute(attributeName);
				String expression = "//*[@" + attributeName + "='" + bankChoiceParentName + "']";
				XPathFactory factory = XPathFactory.newInstance();
				XPath xpath = factory.newXPath();
				try {
					NodeList excelsysNodeList = (NodeList) xpath.evaluate(expression, excelsysDoc, XPathConstants.NODESET);
					boolean choiceFound = false;
					//Por cada Nodo con el mismo nombre del padre del Choice encontrado en Excelsys
					for (int j = 0; j < excelsysNodeList.getLength(); j++) {
						Element excelsysElement = (Element) excelsysNodeList.item(j);
						expression = ".//*[local-name()='choice']";
						NodeList excelsysChoiceNodeList = (NodeList) xpath.evaluate(expression, excelsysElement, XPathConstants.NODESET);
						boolean isSameChoice = false;
						//Por cada Choice de Excelsys
						for (int k = 0; k < excelsysChoiceNodeList.getLength(); k++) {
							Element excelsysChoiceElement = (Element) excelsysChoiceNodeList.item(k);
							NodeList bankChoiceChilds = bankChoiceElement.getChildNodes();
							boolean itemFound = true;
							//Por cada Elemento/Secuencia de un Choice del Banco
							for(int x = 0; x < bankChoiceChilds.getLength(); x++){
								if(bankChoiceChilds.item(x).getNodeType() == Node.ELEMENT_NODE) {
									Element bankChoiceChildElement = (Element) bankChoiceChilds.item(x);
									if(bankChoiceChildElement.getLocalName().compareTo("element") == 0) {
										String refNameElement = bankChoiceChildElement.getAttribute(attributeRef).replaceFirst(".*:", "");
										expression = ".//*[contains(@" + attributeRef + ",'" + refNameElement + "')]";
										NodeList excelsysElementNodeList = (NodeList) xpath.evaluate(expression, excelsysChoiceElement, XPathConstants.NODESET);
										if(excelsysElementNodeList.getLength() == 0){
											expression = ".//*[contains(@" + attributeName + ",'" + refNameElement + "')]";
											excelsysElementNodeList = (NodeList) xpath.evaluate(expression, excelsysChoiceElement, XPathConstants.NODESET);
											if(excelsysElementNodeList.getLength() == 0){
												itemFound = false;
												break;
											}
										}
									}
									if(bankChoiceChildElement.getLocalName().compareTo("sequence") == 0) {
										itemFound = compareSequence(attributeRef, attributeName, excelsysChoiceElement, bankChoiceChildElement);
										if(!itemFound){
											break;
										}
									}
								}
							}
							if(itemFound) {
								isSameChoice = true;
								break;
							}
						}
						if(isSameChoice) {
							choiceFound = true;
							break;
						}
					}
					if(!choiceFound) {
						differences.add(reportMissingChoice(wsdlName, attributeRef, bankChoiceElement, bankChoiceParentName));
					}
				} catch (XPathExpressionException e) {
					e.printStackTrace();
				}
			}
	   }
	   if(!differences.isEmpty()){
		   differences.add("");
	   }
	   return differences;
   }
   

	protected String reportMissingChoice(String wsdlName, String attributeRef, Element bankChoiceElement,String bankChoiceParentName)
	{
		NodeList bankChoiceChilds = bankChoiceElement.getChildNodes();
		StringBuilder differencesMsg = new StringBuilder();
		differencesMsg.append("En el wsdl de Excelsys '"+ wsdlName.replaceFirst("-", "/") + "' no existe 'choice' dentro de " + bankChoiceParentName);
		differencesMsg.append(" con los siguientes elementos: ");
		for(int j = 0; j < bankChoiceChilds.getLength(); j++){
			if(bankChoiceChilds.item(j).getNodeType() == Node.ELEMENT_NODE) {
				Element bankChoiceChildElement = (Element) bankChoiceChilds.item(j);
				if(bankChoiceChildElement.getLocalName().compareTo("element") == 0) {
					differencesMsg.append(bankChoiceChildElement.getAttribute(attributeRef).replaceFirst(".*:", ""));
					if(j == bankChoiceChilds.getLength()-1)
						differencesMsg.append(".");
					else
						differencesMsg.append(", ");
				}
				if(bankChoiceChildElement.getLocalName().compareTo("sequence") == 0) {
					NodeList bankSequenceChilds = bankChoiceChildElement.getChildNodes();
					if(bankSequenceChilds.getLength() > 0) {
						differencesMsg.append(" {");
						for(int y = 0; y < bankSequenceChilds.getLength(); y++) {
							if(bankSequenceChilds.item(y).getNodeType() == Node.ELEMENT_NODE) {
								Element bankSequenceChildElement = (Element) bankSequenceChilds.item(y);
								differencesMsg.append(bankSequenceChildElement.getAttribute(attributeRef).replaceFirst(".*:", ""));
								if(y < bankSequenceChilds.getLength()-1)
									differencesMsg.append(", ");
							}
						}
						differencesMsg.append("}");
						if(j == bankChoiceChilds.getLength()-1)
							differencesMsg.append(".");
						else
							differencesMsg.append(", ");
					}
				}
			}
		}
		return differencesMsg.toString();
	}

	protected boolean compareSequence(String attributeRef, String attributeName, Element excelsysChoiceElement, Element bankChoiceChildElement) throws XPathExpressionException 
	{
		String expression;
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		boolean itemFound = true;
		NodeList bankSequenceChilds = bankChoiceChildElement.getChildNodes();
		//Por cada Elemento de una Secuencia del Banco
		for(int y = 0; y < bankSequenceChilds.getLength(); y++) {
			if(bankSequenceChilds.item(y).getNodeType() == Node.ELEMENT_NODE) {
				Element bankSequenceChildElement = (Element) bankSequenceChilds.item(y);
				String refNameElement = bankSequenceChildElement.getAttribute(attributeRef).replaceFirst(".*:", "");
				expression = ".//*[contains(@" + attributeRef + ",'" + refNameElement + "')]";
				NodeList excelsysElementNodeList = (NodeList) xpath.evaluate(expression, excelsysChoiceElement, XPathConstants.NODESET);
				if(excelsysElementNodeList.getLength() == 0){
					expression = ".//*[contains(@" + attributeName + ",'" + refNameElement + "')]";
					excelsysElementNodeList = (NodeList) xpath.evaluate(expression, excelsysChoiceElement, XPathConstants.NODESET);
					if(excelsysElementNodeList.getLength() == 0){
						itemFound = false;
						break;
					}
				}
			}
		}
		return itemFound;
	}
   

   protected List<String> findMinOccursDifferences(String wsdlName, NodeList bankNodeList, NodeList excelsysNodeList) {
	   
	   String attributeName = "ref";
	   String otherAttributeName = "name";
	  
	   List<String> differences = new ArrayList<String>();
	   for (int i = 0; i < bankNodeList.getLength(); i++) {
			Element bankElement = (Element) bankNodeList.item(i);
			Node bankNodeAttribute = bankElement.getAttributes().getNamedItem(attributeName);
			if(bankElement.getAttributeNode("minOccurs") != null && bankNodeAttribute != null){
				String bankElementName = bankNodeAttribute.getNodeValue();
				bankElementName = bankElementName.replaceFirst(".*?:", "");
				String bankMinOccurs = bankElement.getAttributeNode("minOccurs").getNodeValue();
				for (int j = 0; j < excelsysNodeList.getLength(); j++) {
					Element excelsysElement = (Element) excelsysNodeList.item(j);
					if(excelsysElement.getAttributes().getNamedItem(attributeName) != null || excelsysElement.getAttributes().getNamedItem(otherAttributeName) != null){
						String excelsysMinOccurs = null;
						if(excelsysElement.getAttributeNode("minOccurs") == null){
							excelsysMinOccurs = "-1";
						}
						else
						{
							excelsysMinOccurs = excelsysElement.getAttributeNode("minOccurs").getNodeValue();
						}
						String excelsysElementName = "";
						if(excelsysElement.getAttributes().getNamedItem(attributeName) != null)
							excelsysElementName = excelsysElement.getAttributes().getNamedItem(attributeName).getNodeValue();
						if(excelsysElement.getAttributes().getNamedItem(otherAttributeName) != null)
							excelsysElementName = excelsysElement.getAttributes().getNamedItem(otherAttributeName).getNodeValue();
						
						excelsysElementName = excelsysElementName.replaceFirst(".*?:", "");
						if(bankElementName.compareTo(excelsysElementName) == 0)
						{
							String bankAncestryLine = findAncestryLine(bankElement, bankElementName);
							String excelsysAncestryLine = findAncestryLine(excelsysElement, excelsysElementName);

							if(excelsysAncestryLine.contains(bankAncestryLine) && bankMinOccurs.compareTo(excelsysMinOccurs) != 0) {
								if(!(bankMinOccurs.compareTo("0") == 0 && excelsysMinOccurs.compareTo("-1") == 0)) {
									String output = "WSDL: " + wsdlName.replaceFirst("-", "/") + " | Banco: " + bankAncestryLine + " se encuentra con ocurrencia = " + bankMinOccurs
											+ " | Excelsys: " + excelsysAncestryLine;
									if(excelsysMinOccurs.compareTo("-1") == 0)
										output = output.concat(" no esta definido.");
									else
										output = output.concat(" con ocurrencia = " + excelsysMinOccurs);
									differences.add(output);
								}
							}
						}
					}
				}
			}
					
	   }
	   if(!differences.isEmpty()){
		   differences.add("");
	   }
	   return differences;
	}
	
	protected String findAncestryLine(Element element, String elementName) {
		StringBuilder ancestryLine = new StringBuilder();
		ancestryLine.append(elementName);
		Node nodeAncestry = element.getParentNode();
		while(nodeAncestry != null){
			if(nodeAncestry.getAttributes() != null) {
				if(nodeAncestry.getAttributes().getNamedItem("name") != null){
					ancestryLine.insert(0, nodeAncestry.getAttributes().getNamedItem("name").getNodeValue() + " => ");
				}
				if(nodeAncestry.getAttributes().getNamedItem("base") != null){
					ancestryLine.insert(0, nodeAncestry.getAttributes().getNamedItem("base").getNodeValue().replaceFirst(".*?:", "") + " => ");
				}
			}
			nodeAncestry = nodeAncestry.getParentNode();
		}
		return ancestryLine.toString();
	}
   


	protected Document getEffectiveWsdl(Document docWsdl, String baseUrl)
   {
	   Map<String,String> importMap = new HashMap<String,String>();
	   importMap.put("wsdl:import", "location");
	   importMap.put("xsd:include", "schemaLocation");
	   importMap.put("xsd:import", "schemaLocation");

	   List<String> urlVisited = new ArrayList<String>();

	   docWsdl = appendImportsAndIncludes(docWsdl, baseUrl, importMap, urlVisited);
	   
		return docWsdl;
   }
   
   protected Document appendImportsAndIncludes(Document docWsdl, String baseUrl, Map<String,String> importMap, List<String> urlVisited)
   {
	   String importToFind;
	   String attributeLocation;
	   XPathFactory factory = XPathFactory.newInstance();
	   XPath xpath = factory.newXPath();
	   String expression;
	   
	   Iterator<Entry<String, String>> iterator = importMap.entrySet().iterator();
        while(iterator.hasNext()){
        	importToFind = iterator.next().getKey();
        	attributeLocation = importMap.get(importToFind);
			try {
				expression = "//*[contains(name(), '" + importToFind +"')]";
				NodeList importNodeList = (NodeList) xpath.evaluate(expression, docWsdl, XPathConstants.NODESET);
				for (int i = 0; i < importNodeList.getLength(); i++) {
					String location = importNodeList.item(i).getAttributes().getNamedItem(attributeLocation).getNodeValue();
					
					if(!urlVisited.contains(location)) {
						urlVisited.add(location);
						if(!location.startsWith("http://")) {
							if(location.startsWith("/"))
								location = location.substring(1);
							location =  baseUrl + location;
						}
						String xmlImported = getBodyFromUrl(location);
						Document importedDoc = loadXMLFromString(xmlImported);
						importedDoc = appendImportsAndIncludes(importedDoc, baseUrl, importMap, urlVisited);
						Element docElement = importedDoc.getDocumentElement();
						Node firstDocImportedNode = docWsdl.importNode(docElement, true);
						importNodeList.item(i).getParentNode().appendChild(firstDocImportedNode);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
        }
		return docWsdl;
   }
   
   protected boolean findElementInListByAttribute(String bankElement, NodeList excelsysNodeList, String attributeName)
   {
	   for (int i = 0; i < excelsysNodeList.getLength(); i++) {
		   Node nodo = excelsysNodeList.item(i).getAttributes().getNamedItem(attributeName);
		   if(nodo != null) {
			   String excelsysElement = nodo.getNodeValue();
			   if(attributeName.compareTo("location") == 0) {
			       if(bankElement.endsWith(excelsysElement))
			    	   return true;
			   }
			   else {
				   if(attributeName.compareTo("ref") == 0) {
					   excelsysElement = excelsysElement.replaceFirst(".*:", "");
					   bankElement = bankElement.replaceFirst(".*:", "");
				   }
				   if(bankElement.compareTo(excelsysElement) == 0)
					   return true;
			   }
		   }
	   }
	   return false;
   }

   protected Document loadXMLFromString(String xml) throws Exception
   {
       DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
       factory.setNamespaceAware(true);
       DocumentBuilder builder = factory.newDocumentBuilder();
       return builder.parse(new ByteArrayInputStream(xml.getBytes()));
   }
   
    protected Map<String,String> getBankWsdls(String strUrl, Map<String,String> excelsysWsdlMap)
    {
    	
    	try {
	    	URL bankUrl = new URL(strUrl);
	    	HttpURLConnection con = (HttpURLConnection) bankUrl.openConnection();
	    	InputStream in = con.getInputStream();
	    	if(in == null) {
	    		return null;
	    	}
    	}
    	catch(Exception e) {
    		System.out.println("No se puede acceder a " + strUrl);
    		return null;
    	}
    	
    	Map<String,String> wsdlMap = new HashMap<String,String>();
    	
    	SortedSet<String> list = new TreeSet<String>(excelsysWsdlMap.keySet());

    	for(final String file : list){
			String url = strUrl + file.replaceFirst("-", "/") + "?wsdl";
			System.out.println("Buscando el WSDL: " + url);
			String body = getBodyFromUrl(url);
			if(body == null){
	    		body = "";
	    	}
			if(body.contains("html")){
	    		body = "";
	    	}	
			if(body.compareTo("") != 0) {
				body = normalizeBody(body);
				Document bankDoc = null;
				try {
					bankDoc = loadXMLFromString(body);
					bankDoc = getEffectiveWsdl(bankDoc,strUrl);
					DOMSource domSource = new DOMSource(bankDoc);
					StringWriter writer = new StringWriter();
					StreamResult result = new StreamResult(writer);
					TransformerFactory tf = TransformerFactory.newInstance();
					Transformer transformer = tf.newTransformer();
					transformer.transform(domSource, result);
					body = normalizeBody(writer.toString());
				} catch (Exception e) {
					e.printStackTrace();
				}
				if(!wsdlMap.containsValue(body)){
					wsdlMap.put(file, body);
				}
			}
        }

        return wsdlMap;   			
    }

    protected String getBodyFromUrl(String strUrl)
    {
    	try {
	    	URL url = new URL(strUrl);
	    	HttpURLConnection con = (HttpURLConnection) url.openConnection();
	    	if(con != null) {
		    	InputStream in = null;
		    	if (con.getResponseCode() == 200) {
		    		in = con.getInputStream();
			    	String encoding = con.getContentEncoding();
			    	encoding = encoding == null ? "UTF-8" : encoding;
			    	return IOUtils.toString(in, encoding);
			    }
	    	}
	    	else {
	    		System.out.println("No se pudo acceder a " + strUrl);
	    	}
    	}
    	catch(IOException e){
    		e.printStackTrace();
    	}
    	return "";
    }
    
    protected String normalizeBody(String body)
    {
    		return body.replaceAll(">\\s+<", "><").replaceAll("\\s+", " ");
    }

	
	protected List<String> findMissingWsdls(Map<String, String> bankWsdlMap, Map<String, String> excelsysWsdlMap) 
	{
		List<String> missingWsdls = new ArrayList<String>();
		
		SortedSet<String> wsdlList = new TreeSet<String>(excelsysWsdlMap.keySet());
		for(String wsdlName: wsdlList) {
        	if (bankWsdlMap.get(wsdlName) == null) {
        		missingWsdls.add(wsdlName);
        	}
        	else {
        		if(bankWsdlMap.get(wsdlName) == ""){
        			missingWsdls.add(wsdlName);
        		}
        	}
        }
		return missingWsdls;
	}

    protected void writeWsdls(Map<String, String> wsdlMap, String strDir)
    {
    	try {
	    	File dir = new File(strDir);
	    	if (!dir.exists())
	    		dir.mkdir();
	
	        Iterator<Entry<String, String>> iterator = wsdlMap.entrySet().iterator();
	        while(iterator.hasNext()){
	        	String key = iterator.next().getKey();
	        	String value = wsdlMap.get(key);
	        	File file = new File(strDir + "/" + key + ".xml");
	        	if (!file.exists()) {
					file.createNewFile();
				}
	        	FileWriter fw = new FileWriter(file.getAbsoluteFile());
				BufferedWriter bw = new BufferedWriter(fw);
				bw.write(value);
				bw.close();
	        }
    	}
    	catch(IOException e){
    		e.printStackTrace();
    	}
    }
    
    protected void writeReportInAFile(List<String> foundWsdls, Map<String,List<String>> wsdlsWithDifferences, List<String> missingWsdls, String resultDir, boolean dirExists)
    {
		StringBuilder content = new StringBuilder();
		if(missingWsdls.size() > 0) {
			content.append("WSDLs no encontrados en el Banco:\n\n");
			for(String missingWsdl : missingWsdls){
				content.append(missingWsdl + "\n");
			}
		}
		if(foundWsdls.size() > 0) {
			content.append("\nWSDLs encontrados en el Banco y que fueron analizados para buscar diferencias:\n\n");
			for(String foundWsdl : foundWsdls){
				content.append(foundWsdl + "\n");
			}
		}
		if(wsdlsWithDifferences.containsKey("contentDifferences")) {
			if(wsdlsWithDifferences.get("contentDifferences").size() > 0) {
				content.append("\nWSDLs con diferencias en el contenido:\n\n");
				for(String different : wsdlsWithDifferences.get("contentDifferences")){
					content.append(different + "\n");
				}
			}
		}
		if(wsdlsWithDifferences.containsKey("occursDifferences")) {
			if(wsdlsWithDifferences.get("occursDifferences").size() > 0) {
				
				content.append("\nWSDLs con diferencias en la definicion de minOccurs:\n\n");
				for(String different : wsdlsWithDifferences.get("occursDifferences")){
					content.append(different + "\n");
				}
			}
		}
		if(wsdlsWithDifferences.containsKey("noDifferences")) {
			if(wsdlsWithDifferences.get("noDifferences").size() > 0) {
				
				content.append("\nWSDLs sin diferencias de contenido ni obligatoriedad de campos:\n\n");
				for(String wsdlWithoutDifferences : wsdlsWithDifferences.get("noDifferences")){
					content.append(wsdlWithoutDifferences + "\n");
				}
			}
		}
		if(!wsdlsWithDifferences.containsKey("contentDifferences") && !wsdlsWithDifferences.containsKey("occursDifferences")){
			content.append("\n\nTodos los WSDLs analizados son iguales.");
		}
		if(dirExists) {
	    	try {
		    	String fileWithDifferences = resultDir + "reporte.txt";
		    	File file = new File(fileWithDifferences);
		    	if (!file.exists()) {
					file.createNewFile();
				}
		    	System.out.println("Escribiendo las diferencias en el archivo " + fileWithDifferences);
		    	FileWriter fw = new FileWriter(file.getAbsoluteFile());
				BufferedWriter bw = new BufferedWriter(fw);
	    		bw.write(content.toString());			
				bw.close();
				System.out.println("Finalizada exitosamente la escritura del archivo de diferencias.\n");
	    	}
	    	catch(IOException e){
	    		e.printStackTrace();
	    	}
		}
		else {
			System.out.println(content.toString());
		}
	}

	protected void writeMinOccursDiffInFiles(Map<String, List<String>> wsdlsWithDifferences, String minDifDir)
	{
		File file;
		FileWriter fw;
		BufferedWriter bw;
		StringBuilder content;
		//Escribe las diferencias de campos obligatorios en archivos separados por WSDL
		if(wsdlsWithDifferences.get("occursDifferences") != null){
			if(wsdlsWithDifferences.get("occursDifferences").size() > 0) {
				File dir = new File(minDifDir);
				if (!dir.exists())
					dir.mkdir();
				System.out.println("Comenzando a escribir las diferencias de campos obligatorios en el directorio " + minDifDir);	
				String currentWsdl = "";
				content = new StringBuilder();
				for(String different : wsdlsWithDifferences.get("occursDifferences")){
					String wsdl = different.replaceFirst("\\|.*", "");
					if(currentWsdl.compareTo("") == 0) {
						currentWsdl = wsdl;
						content.append(different.replaceFirst(".*?\\| ", "") + "\n");
					}
					else {
						if(wsdl.compareTo(currentWsdl) == 0) {
							content.append(different.replaceFirst(".*?\\| ", "") + "\n");
						}
						else {
							try {
								file = new File(minDifDir + currentWsdl.replaceFirst("/", "-") + ".txt");
						    	if (!file.exists()) {
									file.createNewFile();
								}
						    	fw = new FileWriter(file.getAbsoluteFile());
								bw = new BufferedWriter(fw);
						    	bw.write(content.toString());			
								bw.close();
								currentWsdl = wsdl;
								content = new StringBuilder();
								content.append(different.replaceFirst(".*?\\| ", "") + "\n");
							}
					    	catch(IOException e){
					    		e.printStackTrace();
					    	}
						}
					}
				}
				System.out.println("Escritura de las diferencias de obligatoriedad completada exitosamente.\n");
			}
		}
	}

}
