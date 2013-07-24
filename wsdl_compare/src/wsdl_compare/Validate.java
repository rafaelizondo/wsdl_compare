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
	
	   public static void main(String args[])
	    {
	    	if(args.length < 2) {
	    		System.out.println("Debes ingresar los siguientes 2 parametros:");
	    		System.out.println("- Directorio donde quedara el archivo con el resultado del analisis.");
//	    		System.out.print("- Directorio a partir del cual quedaran los resultados.");
//	    		System.out.print(" Se creara una carpeta 'bank_wsdls' donde se guardaran los wsdl");
//	    		System.out.print(" y un archivo 'differences.txt' en caso de haber diferencias.\n");
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
	        
	        Map<String,String> elementsToFindMap = new HashMap<String,String>();
	        elementsToFindMap.put("wsdl:operation", "name");
	        elementsToFindMap.put("wsdl:service", "name");
	        elementsToFindMap.put("wsdl:port", "name");
	        elementsToFindMap.put("wsdl:portType", "name");
	        
	        List<String> WsdlsWithDifferences = getDifferences(excelsysWsdlMap,	bankWsdlMap, elementsToFindMap);
	        
	        if (WsdlsWithDifferences.size() == 0 &&  missingWsdls.size() == 0)
	        	System.out.println("Todos los WSDL son iguales");
	        else {
	        	String fileWithDifferences = resultDir + "differences.txt";
	        	writeDiffInAFile(WsdlsWithDifferences, missingWsdls, fileWithDifferences);
	        	System.out.println("Hay diferencias en los WSDL");
	        	System.out.println("Las diferencias estan en el archivo: " + fileWithDifferences);
	        }
	    	//System.out.println("Todos los WSDL obtenidos estan en la siguiente ruta: " + resultDir + "bank_wsdls");
	   }

	public static List<String> getDifferences(Map<String, String> excelsysWsdlMap, Map<String, String> bankWsdlMap, Map<String, String> elementsToFind)
	{
		List<String> WsdlsWithDifferences = new ArrayList<String>();
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
						WsdlsWithDifferences.addAll(getDifferences(key, bankDoc, excelsysDoc, elementKey, elementsToFind.get(elementKey)));
					}
					//WsdlsWithDifferences.addAll(getDifferences(key, bankDoc, excelsysDoc, "wsdl:operation", "name"));
					//WsdlsWithDifferences.addAll(getDifferences(key, bankDoc, excelsysDoc, "wsdl:service", "name"));
					//WsdlsWithDifferences.addAll(getDifferences(key, bankDoc, excelsysDoc, "wsdl:port", "name"));
					//WsdlsWithDifferences.addAll(getDifferences(key, bankDoc, excelsysDoc, "wsdl:portType", "name"));
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return WsdlsWithDifferences;
	}
	   
	   public static List<String> getDifferences(String wsdlName, Document bankDoc, Document excelsysDoc, String tagName, String attributeName) 
	   {
			List<String> differences = new ArrayList<String>();
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();
			String expression;
			expression = "//*[contains(name(), '" + tagName +"')]";
			try {
				NodeList bankNodeList = (NodeList) xpath.evaluate(expression, bankDoc, XPathConstants.NODESET);
				NodeList excelsysNodeList = (NodeList) xpath.evaluate(expression, excelsysDoc, XPathConstants.NODESET);
				for (int i = 0; i < excelsysNodeList.getLength(); i++) {
					String excelsysElement = excelsysNodeList.item(i).getAttributes().getNamedItem(attributeName).getNodeValue();
					if(!findElementByName(excelsysElement,bankNodeList))
						differences.add("En el wsdl '"+ wsdlName + "' no existe el elemento '" + tagName + "' con atributo " + attributeName + "='" + excelsysElement + "'.");
			}
			} catch (XPathExpressionException e) {
				e.printStackTrace();
			}
		   
		   return differences;
	   }
	   
	   public static Document getEffectiveWsdl(Document docWsdl, String baseUrl)
	   {
		   XPathFactory factory = XPathFactory.newInstance();
		   XPath xpath = factory.newXPath();
		   String expression;
			expression = "//*[contains(name(), 'wsdl:import')]";
			try {
				NodeList importNodeList = (NodeList) xpath.evaluate(expression, docWsdl, XPathConstants.NODESET);
				for (int i = 0; i < importNodeList.getLength(); i++) {
					String location = importNodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();
					//System.out.println("     Estos son los import: " + location);
					if(!location.startsWith("http://")) {
						if(location.startsWith("/"))
							location = location.substring(1);
						location =  baseUrl + location;
					}
					String xmlImported = getBodyFromUrl(location);
					Document importedDoc = loadXMLFromString(xmlImported);
					importedDoc = getEffectiveWsdl(importedDoc,baseUrl);
					Element docElement = importedDoc.getDocumentElement();
					if (importNodeList.item(i).getParentNode() == null)
						System.out.println("importNodeList no tiene padre");
					else
					{
						Node firstDocImportedNode = docWsdl.importNode(docElement, true);
						importNodeList.item(i).getParentNode().appendChild(firstDocImportedNode);
					}
				}
				expression = "//*[contains(name(), 'xsd:include')]";
				importNodeList = (NodeList) xpath.evaluate(expression, docWsdl, XPathConstants.NODESET);
				for (int i = 0; i < importNodeList.getLength(); i++) {
					String location = importNodeList.item(i).getAttributes().getNamedItem("schemaLocation").getNodeValue();
					//System.out.println("     Estos son los import de xsd: " + location);
					if(!location.startsWith("http://")) {
						if(location.startsWith("/"))
							location = location.substring(1);
						location =  baseUrl + location;
					}
					String xmlImported = getBodyFromUrl(location);
					Document importedDoc = loadXMLFromString(xmlImported);
					importedDoc = getEffectiveWsdl(importedDoc,baseUrl);
					Element docElement = importedDoc.getDocumentElement();
					if (importNodeList.item(i).getParentNode() == null)
						System.out.println("importNodeList no tiene padre");
					else
					{
						Node firstDocImportedNode = docWsdl.importNode(docElement, true);
						importNodeList.item(i).getParentNode().appendChild(firstDocImportedNode);
					}
				}
				expression = "//*[contains(name(), 'xsd:import')]";
				importNodeList = (NodeList) xpath.evaluate(expression, docWsdl, XPathConstants.NODESET);
				for (int i = 0; i < importNodeList.getLength(); i++) {
					String location = importNodeList.item(i).getAttributes().getNamedItem("schemaLocation").getNodeValue();
					//System.out.println("     Estos son los import de xsd: " + location);
					if(!location.startsWith("http://")) {
						if(location.startsWith("/"))
							location = location.substring(1);
						location =  baseUrl + location;
					}
					String xmlImported = getBodyFromUrl(location);
					Document importedDoc = loadXMLFromString(xmlImported);
					importedDoc = getEffectiveWsdl(importedDoc,baseUrl);
					Element docElement = importedDoc.getDocumentElement();
					if (importNodeList.item(i).getParentNode() == null)
						System.out.println("importNodeList no tiene padre");
					else
					{
						Node firstDocImportedNode = docWsdl.importNode(docElement, true);
						importNodeList.item(i).getParentNode().appendChild(firstDocImportedNode);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return docWsdl;
	   }
	   
	   public static boolean findElementByName(String elementName, NodeList nodeList)
	   {
		   for (int i = 0; i < nodeList.getLength(); i++) {
			   if(elementName.compareTo(nodeList.item(i).getAttributes().getNamedItem("name").getNodeValue()) == 0)
				   return true;
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
		    		//System.out.println("No se pudo obtener el body de esta url: " + url);
		    		body = "";
		    	}
				if(body.contains("html")){
		    		//System.out.println("Esta url respondio un html: " + url);
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
				}
					
	        	wsdlMap.put(name, body);
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
		    	return "";
	    	}
	    	catch(IOException e){
	    		System.out.println(e.getMessage());
	    		return null;
	    	}
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
	    		System.out.println(e.getMessage());
	    	}
	    }
	    
	    private static void writeDiffInAFile(List<String> wsdlsWithDifferences, List<String> missingWsdls, String strFile)
	    {
	    	try {
		    	File file = new File(strFile);
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
				if(wsdlsWithDifferences.size() > 0) {
					content.append("\nWSDLs con diferencias en el contenido:\n\n");
					for(String different : wsdlsWithDifferences){
						content.append(different + "\n");
					}
				}
				bw.write(content.toString());			
				bw.close();
	    	}
	    	catch(IOException e){
	    		System.out.println(e.getMessage());
	    	}
		}


}
