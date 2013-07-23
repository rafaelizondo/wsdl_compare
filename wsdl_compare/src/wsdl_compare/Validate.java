package wsdl_compare;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.net.URL;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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
		   
	    	if(args.length < 1) {
	    		System.out.println("Debes ingresar la URL base del banco donde estan los WSDL.");
	    		System.exit(0);
	    	}
	    	String bankUrl = args[0];

	    	if(!bankUrl.endsWith("/"))
	    		bankUrl += "/";
	    	
	        Map<String,String> excelsysWsdlMap = getWsdlsFromResources();

	    	Map<String, String> bankWsdlMap = getBankWsdls(bankUrl);
	    	
	        List<String> missingWsdls = findMissingWsdls(bankWsdlMap, excelsysWsdlMap);

	        for(String missing: missingWsdls){
	        	System.out.println("No existe este WSDL: " + missing);
	        }
	        
			Iterator<Entry<String, String>> iterator = bankWsdlMap.entrySet().iterator();
	        while(iterator.hasNext()){
	        	String key = iterator.next().getKey();
	        	try {
					String bankXML = bankWsdlMap.get(key);
					if (!bankXML.isEmpty()) {
						Document bankDoc = loadXMLFromString(bankXML);
						Document excelsysDoc = loadXMLFromString(excelsysWsdlMap.get(key));
						XPathFactory factory = XPathFactory.newInstance();
						XPath xpath = factory.newXPath();
						String expression;
						expression = "//*[contains(name(), 'wsdl:operation')]";
						NodeList bankNodeList = (NodeList) xpath.evaluate(expression, bankDoc, XPathConstants.NODESET);
						NodeList excelsysNodeList = (NodeList) xpath.evaluate(expression, excelsysDoc, XPathConstants.NODESET);
	
						for (int i = 0; i < excelsysNodeList.getLength(); i++) {
							String excelsysOperation = excelsysNodeList.item(i).getAttributes().getNamedItem("name").getNodeValue();
							if(!findElementByName(excelsysOperation,bankNodeList))
								System.out.println("En el wsdl '"+ key + "' no existe esta operacion: " + excelsysOperation);
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
	        }
			
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
		    		System.out.println("No se pudo obtener el body de esta url: " + url);
		    		body = "";
		    	}
				if(body.contains("html")){
		    		System.out.println("Esta url respondio un html: " + url);
		    		body = "";
		    	}	
				else
					body = normalizeBody(body);
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
					wsdlMap.put(key, content);
	        	} catch (FileNotFoundException e) {
					e.printStackTrace();
	    	    } catch (IOException e) {
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

}
