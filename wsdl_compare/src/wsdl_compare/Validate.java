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
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class Validate {
	
		//static String excelsysUrlBase = "http://ws.excelsys.co/icbs-bus-simulator-web";
	
	   public static void main(String args[])
	    {
	    	if(args.length < 2) {
	    		System.out.println("Debes ingresar los siguientes 2 parametros:");
	    		System.out.println("- Directorio donde quedara el archivo con el resultado del analisis.");
	    		System.out.println("- URL base del banco donde estan los links a todos los WSDL.");
	    		System.exit(0);
	    	}
	    	String resultDir = args[0];
	    	String bankUrl = args[1];

	    	if(!resultDir.endsWith("/"))
	    		resultDir += "/";
	    	File dir = new File(resultDir);
	    	if (!dir.exists())
	    		dir.mkdir();
	    	if(!bankUrl.endsWith("/"))
	    		bankUrl += "/";
	    	
	        Map<String,String> excelsysWsdlMap = getEffectiveWsdlsFromResources();
	        
	        //writeWsdls(excelsysWsdlMap, "/Users/rafael/wsdl_compare/wsdl_compare/resources/excelsys_effective_wsdls");

	    	Map<String, String> bankWsdlMap = getBankWsdls(bankUrl);
	    	
	    	//writeWsdls(bankWsdlMap, "/Users/rafael/wsdl_compare/results/bank_wsdls/effective_wsdls");
	    	
	        List<String> missingWsdls = findMissingWsdls(bankWsdlMap, excelsysWsdlMap);
	        
	        Map<String,String> elementsToCompareMap = new HashMap<String,String>();

//	        elementsToCompareMap.put("operation", "name");
//	        elementsToCompareMap.put("service", "name");
//	        elementsToCompareMap.put("port", "name");
//	        elementsToCompareMap.put("portType", "name");
//	        elementsToCompareMap.put("complexType", "name");
//	        elementsToCompareMap.put("address", "location");
	        elementsToCompareMap.put("element", "name");
	        
	        Map<String, List<String>> WsdlsWithDifferences = getDifferencesOnMaps(excelsysWsdlMap,	bankWsdlMap, elementsToCompareMap);
	        
	        if (WsdlsWithDifferences.size() == 0 &&  missingWsdls.size() == 0)
	        	System.out.println("Todos los WSDL son iguales");
	        else {
	        	writeDiffInAFile(WsdlsWithDifferences, missingWsdls, resultDir);
	        	System.out.println("Hay diferencias en los WSDL");
	        	System.out.println("Las diferencias estan en el archivo: " + resultDir + "differences.txt");
	        }
	   }

	public static Map<String, List<String>> getDifferencesOnMaps(Map<String, String> excelsysWsdlMap, Map<String, String> bankWsdlMap, Map<String, String> elementsToFind)
	{
		Map<String,List<String>> differencesMap = new HashMap<String,List<String>>();
		Map<String,List<String>> returnMap = new HashMap<String,List<String>>();
		Iterator<Entry<String, String>> bankWsdlIterator = bankWsdlMap.entrySet().iterator();
		while(bankWsdlIterator.hasNext()){
			String key = bankWsdlIterator.next().getKey();
			try {
				String bankXML = bankWsdlMap.get(key);
				if (!bankXML.isEmpty()) {
					Document bankDoc = loadXMLFromString(bankXML);
					Document excelsysDoc = loadXMLFromString(excelsysWsdlMap.get(key));
					Iterator<Entry<String, String>> elementIterator = elementsToFind.entrySet().iterator();
					while(elementIterator.hasNext()){
						String elementKey = elementIterator.next().getKey();
						returnMap = getDifferencesOnDocs(key, bankDoc, excelsysDoc, elementKey, elementsToFind.get(elementKey));
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

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return differencesMap;
	}
	   
	   public static Map<String, List<String>> getDifferencesOnDocs(String wsdlName, Document bankDoc, Document excelsysDoc, String tagName, String attributeName) 
	   {
			List<String> contentDifferences = new ArrayList<String>();
			List<String> occursDifferences = new ArrayList<String>();
			List<String> reported = new ArrayList<String>();
			Map<String,List<String>> differencesMap = new HashMap<String,List<String>>();
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();
			String expression;
			expression = "//*[local-name()='" + tagName + "']";
			try {
				
				NodeList bankNodeList = (NodeList) xpath.evaluate(expression, bankDoc, XPathConstants.NODESET);
				NodeList excelsysNodeList = (NodeList) xpath.evaluate(expression, excelsysDoc, XPathConstants.NODESET);
				
				
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
								str.append("En el wsdl '"+ wsdlName.replaceFirst("-", "/") + "' no existe '" + tagName + "' con atributo " + attributeName + "='" + bankElement + "'. ");
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
	   
	   public static List<String> findMinOccursDifferences(String wsdlName, NodeList bankNodeList, NodeList excelsysNodeList) {
		   
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
								StringBuilder bankAncestryLine = findAncestryLine(bankElement, bankElementName);
								StringBuilder excelsysAncestryLine = findAncestryLine(excelsysElement, excelsysElementName);

								if(excelsysAncestryLine.toString().contains(bankAncestryLine) && bankMinOccurs.compareTo(excelsysMinOccurs) != 0) {
									if(!(bankMinOccurs.compareTo("0") == 0 && excelsysMinOccurs.compareTo("-1") == 0)) {
										String output = wsdlName.replaceFirst("-", "/") + "| Banco: " + bankAncestryLine + " se encuentra con ocurrencia = " + bankMinOccurs
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
		   
		return differences;
	}

	public static StringBuilder findAncestryLine(Element element, String elementName) {
		StringBuilder ancestryLine = new StringBuilder();
		ancestryLine.append(elementName);
		Node nodeAncestry = element.getParentNode();
		while(nodeAncestry != null){
			if(nodeAncestry.getAttributes() != null) {
				if(nodeAncestry.getAttributes().getNamedItem("name") != null){
					ancestryLine.append("=>" + nodeAncestry.getAttributes().getNamedItem("name").getNodeValue());
				}
				if(nodeAncestry.getAttributes().getNamedItem("base") != null){
					ancestryLine.append("=>" + nodeAncestry.getAttributes().getNamedItem("base").getNodeValue().replaceFirst(".*?:", ""));
				}
			}
			nodeAncestry = nodeAncestry.getParentNode();
		}
		return ancestryLine;
	}
	   


	public static Document getEffectiveWsdl(Document docWsdl, String baseUrl)
	   {
		   Map<String,String> importMap = new HashMap<String,String>();
		   importMap.put("wsdl:import", "location");
		   importMap.put("xsd:include", "schemaLocation");
		   importMap.put("xsd:import", "schemaLocation");

		   List<String> urlVisited = new ArrayList<String>();

		   docWsdl = appendImportsAndIncludes(docWsdl, baseUrl, importMap, urlVisited);
		   
			return docWsdl;
	   }
	   
	   public static Document appendImportsAndIncludes(Document docWsdl, String baseUrl, Map<String,String> importMap, List<String> urlVisited)
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
	   
	   public static boolean findElementInListByAttribute(String bankElement, NodeList excelsysNodeList, String attributeName)
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

	   public static Document loadXMLFromString(String xml) throws Exception
	   {
	       DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	       factory.setNamespaceAware(true);
	       DocumentBuilder builder = factory.newDocumentBuilder();
	       return builder.parse(new ByteArrayInputStream(xml.getBytes()));
	   }
	   
	    public static Map<String,String> getBankWsdls(String strUrl)
	    {
	    	Map<String,String> wsdlMap = new HashMap<String,String>();
	        Pattern pattern;
	        pattern = Pattern.compile(".*excelsys_wsdls.*xml");
	    	final Collection<String> list = ResourceList.getResources(pattern);
	    	String name;
	        for(final String file : list){
				name = file.replaceFirst(".xml", "").replaceFirst(".*excelsys_wsdls/", "");
				String url = strUrl + name.replaceFirst("-", "/") + "?wsdl";
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
						wsdlMap.put(name, body);
					}
				}
	        }

	        return wsdlMap;   			
	    }

	    public static String getBodyFromUrl(String strUrl)
	    {
	    	try {
		    	URL url = new URL(strUrl);
		    	HttpURLConnection con = (HttpURLConnection)url.openConnection();
		    	InputStream in = null;
		    	if (con.getResponseCode() == 200) {
		    		in = con.getInputStream();
			    	String encoding = con.getContentEncoding();
			    	encoding = encoding == null ? "UTF-8" : encoding;
			    	return IOUtils.toString(in, encoding);
			    }
	    	}
	    	catch(IOException e){
	    		e.printStackTrace();
	    	}
	    	return "";
	    }
	    
	    public static String normalizeBody(String body)
	    {
	    		return body.replaceAll(">\\s+<", "><").replaceAll("\\s+", " ");
	    }

		public static Map<String,String> getEffectiveWsdlsFromResources()
	    {
	    	Map<String,String> wsdlMap = new HashMap<String,String>();
	        Pattern pattern;
	        pattern = Pattern.compile(".*excelsys_effective_wsdls.*xml");
	    	final Collection<String> list = ResourceList.getResources(pattern);
	        for(String file : list){
	        	try {
	        		file = file.replaceFirst(".*excelsys_effective_wsdls", "excelsys_effective_wsdls");
	        		InputStream inputStream = Test.class.getClassLoader().getResourceAsStream(file);
			        String content = IOUtils.toString(inputStream);
			        inputStream.close();
			        String key = file.replaceFirst(".xml", "").replaceFirst(".*excelsys_effective_wsdls/", "");
					wsdlMap.put(key, content);
	        	} catch (FileNotFoundException e) {
					e.printStackTrace();
	    	    } catch (IOException e) {
					e.printStackTrace();
	    	    } catch (Exception e) {
					e.printStackTrace();
				}
	        }
	    	return wsdlMap;
	    }
		
		public static Map<String,String> getWsdlsFromResources()
	    {
	    	Map<String,String> wsdlMap = new HashMap<String,String>();
	        Pattern pattern;
	        pattern = Pattern.compile(".*excelsys_wsdls.*xml");
	    	final Collection<String> list = ResourceList.getResources(pattern);
	        for(String file : list){
	        	try {
	        		file = file.replaceFirst(".*excelsys_wsdls", "excelsys_wsdls");
	        		InputStream inputStream = Test.class.getClassLoader().getResourceAsStream(file);
			        String content = IOUtils.toString(inputStream);
			        inputStream.close();
			        String key = file.replaceFirst(".xml", "").replaceFirst(".*excelsys_wsdls/", "");

			        Document excelsysDoc = loadXMLFromString(content);
					excelsysDoc = getEffectiveWsdl(excelsysDoc, "");
					DOMSource domSource = new DOMSource(excelsysDoc);
					StringWriter writer = new StringWriter();
					StreamResult result = new StreamResult(writer);
					TransformerFactory tf = TransformerFactory.newInstance();
					Transformer transformer = tf.newTransformer();
					transformer.transform(domSource, result);
					content = normalizeBody(writer.toString());
			        
					wsdlMap.put(key, content);

	        	} catch (FileNotFoundException e) {
					e.printStackTrace();
	    	    } catch (IOException e) {
					e.printStackTrace();
	    	    } catch (Exception e) {
					e.printStackTrace();
				}
	        }
	    	return wsdlMap;
	    }

		public static List<String> findMissingWsdls(Map<String, String> bankWsdlMap, Map<String, String> excelsysWsdlMap) 
		{
			List<String> missingWsdls = new ArrayList<String>();
			Iterator<Entry<String, String>> iterator = excelsysWsdlMap.entrySet().iterator();
	        while(iterator.hasNext()){
	        	String key = iterator.next().getKey();
	        	if (bankWsdlMap.get(key) == null) {
	        		missingWsdls.add(key);
	        	}
	        	else {
	        		if(bankWsdlMap.get(key) == ""){
	        			missingWsdls.add(key);
	        		}
	        	}
	        }
			return missingWsdls;
		}

	    public static void writeWsdls(Map<String, String> wsdlMap, String strDir)
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
	    
	    private static void writeDiffInAFile(Map<String,List<String>> wsdlsWithDifferences, List<String> missingWsdls, String resultDir)
	    {
	    	String fileWithDifferences = resultDir + "differences.txt";
	    	try {
		    	File file = new File(fileWithDifferences);
		    	if (!file.exists()) {
					file.createNewFile();
				}
		    	FileWriter fw = new FileWriter(file.getAbsoluteFile());
				BufferedWriter bw = new BufferedWriter(fw);
				StringBuilder content = new StringBuilder();
				if(missingWsdls.size() > 0) {
					content.append("WSDLs no encontrados:\n\n");
					for(String missing : missingWsdls){
						content.append(missing + "\n");
					}
				}
				if(wsdlsWithDifferences.get("contentDifferences").size() > 0) {
					content.append("\nWSDLs con diferencias en el contenido:\n\n");
					for(String different : wsdlsWithDifferences.get("contentDifferences")){
						content.append(different + "\n");
					}
				}
				if(wsdlsWithDifferences.get("occursDifferences").size() > 0) {
					
					content.append("\nWSDLs con diferencias en la definicion de minOccurs:\n\n");
					for(String different : wsdlsWithDifferences.get("occursDifferences")){
						content.append(different + "\n");
					}
				}				
				bw.write(content.toString());			
				bw.close();

				String minDifDir = resultDir + "minoccurs_differences/";
		    	File dir = new File(minDifDir);
		    	if (!dir.exists())
		    		dir.mkdir();
		    	
				if(wsdlsWithDifferences.get("occursDifferences").size() > 0) {
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
						}
					}
				}				
	    	}
	    	catch(IOException e){
	    		e.printStackTrace();
	    	}
		}


}
