package wsdl_compare;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

public class Test{
    public static void main_alternative(String args[])
    {
    	if(args.length < 2) {
    		System.out.println("Debes ingresar los siguientes 2 parametros:");
    		System.out.print("- Directorio a partir del cual quedaran los resultados.");
    		System.out.print(" Se creara una carpeta 'bank_wsdls' donde se guardaran los wsdl");
    		System.out.print(" y un archivo 'differences.txt' en caso de haber diferencias.\n");
    		System.out.println("- URL base del banco donde estan los WSDL.");
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
    	
    	Map<String, List<String>> bankWsdlMap = getBankWsdls(bankUrl);
        writeWsdls(bankWsdlMap, resultDir + "bank_wsdls");
        
        Map<String, List<String>> excelsysWsdlMap = getWsdlsFromResources();
        
        List<String> missingWsdls = findMissingWsdls(bankWsdlMap, excelsysWsdlMap);
        Map<String, List<String>> WsdlsWithDifferences = findWsdlsWithDifference(excelsysWsdlMap, bankWsdlMap, bankUrl);
    	
        if (WsdlsWithDifferences.get("wsdl").size() == 0 && WsdlsWithDifferences.get("xsd").size() == 0 &&  missingWsdls.size() == 0)
        	System.out.println("Todos los WSDL y sus XSD son iguales");
        else {
        	String fileWithDifferences = resultDir + "differences.txt";
        	writeDiffInAFile(WsdlsWithDifferences, missingWsdls, fileWithDifferences);
        	if (WsdlsWithDifferences.get("wsdl").size() > 0 && WsdlsWithDifferences.get("xsd").size() == 0)
        		System.out.println("Hay diferencias en los WSDL");
        	if (WsdlsWithDifferences.get("wsdl").size() == 0 && WsdlsWithDifferences.get("xsd").size() > 0)
        		System.out.println("Hay diferencias en los XSD");
        	if (WsdlsWithDifferences.get("wsdl").size() > 0 && WsdlsWithDifferences.get("xsd").size() > 0)
        		System.out.println("Hay diferencias en los WSDL y XSD");
        	System.out.println("Las diferencias estan en el archivo: " + fileWithDifferences);
        }
    	System.out.println("Todos los WSDL obtenidos estan en la siguiente ruta: " + resultDir + "bank_wsdls");
    }

	public static List<String> findMissingWsdls(Map<String, List<String>> bankWsdlMap, Map<String, List<String>> excelsysWsdlMap) 
	{
		List<String> missingWsdls = new ArrayList<String>();
		Map<String,List<String>> fisrtWsdlMap = new HashMap<String,List<String>>();
		Map<String,List<String>> otherWsdlMap = new HashMap<String,List<String>>();
		if(excelsysWsdlMap.size() > bankWsdlMap.size()) {
    		fisrtWsdlMap = excelsysWsdlMap;
    		otherWsdlMap = bankWsdlMap;
		}
		else {
    		fisrtWsdlMap = bankWsdlMap;
    		otherWsdlMap = excelsysWsdlMap;
		}
		Iterator<Entry<String, List<String>>> iterator = fisrtWsdlMap.entrySet().iterator();
        while(iterator.hasNext()){
        	String key = iterator.next().getKey();
        	if (otherWsdlMap.get(key) == null) {
        		missingWsdls.add(key);
        	}
        }
		return missingWsdls;
	}
    
    private static void writeDiffInAFile(Map<String, List<String>> wsdlsWithDifferences, List<String> missingWsdls, String strFile)
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
			List<String> differences = wsdlsWithDifferences.get("wsdl");
			if(differences.size() > 0) {
				content.append("\nWSDLs con diferencias en el contenido:\n\n");
				for(String different : differences){
					content.append(different + "\n");
				}
			}
			differences = wsdlsWithDifferences.get("xsd");
			if(differences.size() > 0) {
				content.append("\nXSDs con diferencias en el contenido:\n\n");
				for(String different : differences){
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

    public static Map<String,String> getBankWsdls(String strUrl, String strDir)
    {
    	String file;
    	File folder = new File(strDir);
    	File[] listOfFiles = folder.listFiles();
    	Map<String,String> wsdlMap = new HashMap<String,String>();

    	for (int i = 0; i < listOfFiles.length; i++) 
    	{
    		if (listOfFiles[i].isFile()) 
    		{
    			file = listOfFiles[i].getName().replaceFirst(".xml", "");
    			String url = strUrl + file.replaceFirst("-", "/") + "?wsdl";
    			String body = getBodyFromUrl(url);
    			if(body == null){
    	    		System.out.println("No se pudo obtener el body de esta url: " + url);
    	    		System.exit(0);
    	    	}
	        	body = normalizeBody(body);
	        	wsdlMap.put(file, body);
    		}
    	}
        return wsdlMap;   			
    }
    
    public static Map<String, List<String>> getBankWsdls(String strUrl)
    {
    	Map<String, List<String>> wsdlMap = new HashMap<String,List<String>>();
        Pattern pattern;
        pattern = Pattern.compile(".*excelsys_wsdls.*xml");
    	final Collection<String> list = ResourceList.getResources(pattern);
    	String name;
        for(final String file : list){
			name = file.replaceFirst(".xml", "").replaceFirst(".*excelsys_wsdls/", "");
			String url = strUrl + name.replaceFirst("-", "/") + "?wsdl";
			String body = getBodyFromUrl(url);
			if(body == null){
	    		System.out.println("No se pudo obtener el contenido de esta url: " + url);
	    		System.exit(0);
	    	}
        	body = normalizeBody(body);
        	List<String> serviceDefinition = new ArrayList<String>();
        	serviceDefinition.add(body);
        	
        	String xsdUrl = getXsdUrlFromWsdl(body);
        	if(xsdUrl != null) {
        		String xsd = getBodyFromUrl(xsdUrl);
    			if(xsd == null){
    	    		System.out.println("getBankWsdls: No se pudo obtener el contenido de esta url: " + xsdUrl + " obtenida desde: " + name);
    	    		//System.exit(0);
    	    	}
    			else {
		        	xsd = normalizeBody(xsd);
		        	serviceDefinition.add(xsd);
    			}
        	}
        	wsdlMap.put(name, serviceDefinition);
        }
        return wsdlMap;
    }
    
    public static String normalizeBody(String body)
    {
    		return body.replaceAll(">\\s+<", "><").replaceAll("\\s+", " ");
    }
    
	public static Map<String,String> getWsdlsFromFiles(String dir)
    {
    	String file;
    	File folder = new File(dir);
    	File[] listOfFiles = folder.listFiles();
    	Map<String,String> wsdlMap = new HashMap<String,String>();

    	for (int i = 0; i < listOfFiles.length; i++) 
    	{
    		if (listOfFiles[i].isFile()) 
    		{
    			file = listOfFiles[i].getName();
	    	    FileInputStream inputStream;
				try {
					inputStream = new FileInputStream(dir + "/" + file);
	    	        String content = IOUtils.toString(inputStream);
	    	        inputStream.close();
					wsdlMap.put(file.replaceFirst(".xml", ""), content);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
	    	    } catch (IOException e) {
					e.printStackTrace();
	    	    }
    		}
   		}
    	return wsdlMap;
    }
    
	public static Map<String, List<String>> getWsdlsFromResources()
    {
    	Map<String, List<String>> wsdlMap = new HashMap<String, List<String>>();
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
		        
		    	List<String> serviceDefinition = new ArrayList<String>();
		        serviceDefinition.add(content);
		        
	        	String xsdUrl = getXsdUrlFromWsdl(content);
	        	if(xsdUrl != null) {
	        		String xsd = getBodyFromUrl(xsdUrl);
	    			if(xsd == null){
	    	    		System.out.println("getWsdlsFromResources: No se pudo obtener el contenido de esta url: " + xsdUrl + " obtenida desde: " + key);
	    	    		//System.exit(0);
	    	    	}
	    			else {
			        	xsd = normalizeBody(xsd);
			        	serviceDefinition.add(xsd);
	    			}
	        	}

		        
				wsdlMap.put(key, serviceDefinition);
        	} catch (FileNotFoundException e) {
				e.printStackTrace();
    	    } catch (IOException e) {
				e.printStackTrace();
    	    }
        }
    	return wsdlMap;
    }
    public static Map<String,List<String>> findWsdlsWithDifference(Map<String,List<String>> excelsysWsdlMap, Map<String,List<String>> bankWsdlMap, String bankUrl)
    {
        Iterator<Entry<String, List<String>>> iterator = excelsysWsdlMap.entrySet().iterator();
        Map<String,List<String>> differences = new HashMap<String,List<String>>();
        List<String> wsdlDifferences = new ArrayList<String>();
        List<String> xsdDifferences = new ArrayList<String>();
        while(iterator.hasNext()){
        	String key = iterator.next().getKey();
        	if (bankWsdlMap.get(key) != null) {
        		String excelsysBody = excelsysWsdlMap.get(key).get(0);
        		String bankBody = bankWsdlMap.get(key).get(0);
        		excelsysBody = excelsysBody.replaceAll("http://ws.excelsys.co/icbs-bus-simulator-web/", "");
        		bankBody = bankBody.replaceAll(bankUrl, "");
	        	if (!excelsysBody.equals(bankBody)) {
	        		wsdlDifferences.add(key);
	        	}
	        	excelsysBody = excelsysWsdlMap.get(key).get(1);
	        	bankBody = bankWsdlMap.get(key).get(1);
	        	if (!excelsysBody.equals(bankBody)) {
	        		xsdDifferences.add(key);
	        	}
        	}
        }
        differences.put("wsdl",wsdlDifferences);
        differences.put("xsd",xsdDifferences);
        return differences;
    }
    
    public static Map<String,String> getAllWsdls(String url)
    {
    	Map<String,String> wsdlMap = new HashMap<String,String>();
		String body = getBodyFromUrl(url);

    	String pattern = "(\\<a href=\")(.*?)(\\?wsdl)";
    	Pattern r = Pattern.compile(pattern);
    	Matcher m = r.matcher(body);

    	while (m.find( )) {
        	body = getBodyFromUrl(m.group(2) + m.group(3));
        	body = body.replaceAll(">\\s+<", "><").replaceAll("\\s+", " ");
        	wsdlMap.put(m.group(2).replaceFirst(url, "").replaceAll("/", "-"), body);
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
    
    public static void writeWsdls(Map<String, List<String>> wsdlMap, String strDir)
    {
    	try {
    		String wsdlDir = strDir + "/wsdl";
	    	File dir = new File(wsdlDir);
	    	if (!dir.exists())
	    		dir.mkdir();
	    	String xsdDir = strDir + "/xsd";
	    	dir = new File(xsdDir);
	    	if (!dir.exists())
	    		dir.mkdir();
	
	        Iterator<Entry<String, List<String>>> iterator = wsdlMap.entrySet().iterator();
	        while(iterator.hasNext()){
	        	String key = iterator.next().getKey();
	        	List<String> value = wsdlMap.get(key);
	        	File file = new File(wsdlDir + "/" + key + ".xml");
	        	if (!file.exists()) {
					file.createNewFile();
				}
	        	FileWriter fw = new FileWriter(file.getAbsoluteFile());
				BufferedWriter bw = new BufferedWriter(fw);
				bw.write(value.get(0));
				bw.close();

	        	file = new File(xsdDir + "/" + key + ".xml");
	        	if (!file.exists()) {
					file.createNewFile();
				}
	        	fw = new FileWriter(file.getAbsoluteFile());
				bw = new BufferedWriter(fw);
				bw.write(value.get(1));
				bw.close();

	        }
    	}
    	catch(IOException e){
    		System.out.println(e.getMessage());
    	}
    }
    
    public static String getXsdUrlFromWsdl(String body)
    {
    	String pattern = "(location=\")(.*?)(\")";
    	Pattern r = Pattern.compile(pattern);
    	Matcher m = r.matcher(body);

    	if (m.find( )) {
    		return m.group(2);
    	}
    	return null;
    }
}

