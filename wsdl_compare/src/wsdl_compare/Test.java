package wsdl_compare;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
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
    public static void main(String args[])
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
    	Map<String,String> bankWsdlMap = getBankWsdls(bankUrl);
        writeWsdls(bankWsdlMap, resultDir + "bank_wsdls");
        
        Map<String,String> excelsysWsdlMap = getWsdlsFromResources();
        
        List<String> missingWsdls = findMissingWsdls(bankWsdlMap, excelsysWsdlMap);
    	List<String> WsdlsWithDifferences = findWsdlsWithDifference(excelsysWsdlMap, bankWsdlMap, bankUrl);
    	
        if (WsdlsWithDifferences.size() == 0 &&  missingWsdls.size() == 0)
        	System.out.println("Todos los WSDL son iguales");
        else {
        	String fileWithDifferences = resultDir + "differences.txt";
        	writeDiffInAFile(WsdlsWithDifferences, missingWsdls, fileWithDifferences);
        	System.out.println("Hay diferencias en los WSDL");
        	System.out.println("Las diferencias estan en el archivo: " + fileWithDifferences);
        }
    	System.out.println("Todos los WSDL obtenidos estan en la siguiente ruta: " + resultDir + "bank_wsdls");
    }

	public static List<String> findMissingWsdls(Map<String, String> bankWsdlMap, Map<String, String> excelsysWsdlMap) 
	{
		List<String> missingWsdls = new ArrayList<String>();
		Map<String,String> fisrtWsdlMap = new HashMap<String,String>();
		Map<String,String> otherWsdlMap = new HashMap<String,String>();
		if(excelsysWsdlMap.size() > bankWsdlMap.size()) {
    		fisrtWsdlMap = excelsysWsdlMap;
    		otherWsdlMap = bankWsdlMap;
		}
		else {
    		fisrtWsdlMap = bankWsdlMap;
    		otherWsdlMap = excelsysWsdlMap;
		}
		Iterator<Entry<String, String>> iterator = fisrtWsdlMap.entrySet().iterator();
        while(iterator.hasNext()){
        	String key = iterator.next().getKey();
        	if (otherWsdlMap.get(key) == null) {
        		missingWsdls.add(key);
        	}
        }
		return missingWsdls;
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
	    		System.exit(0);
	    	}
        	body = normalizeBody(body);
        	wsdlMap.put(name, body);
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
    public static List<String> findWsdlsWithDifference(Map<String,String> excelsysWsdlMap, Map<String,String> bankWsdlMap, String bankUrl)
    {
        Iterator<Entry<String, String>> iterator = excelsysWsdlMap.entrySet().iterator();
        List<String> differences = new ArrayList<String>();
        while(iterator.hasNext()){
        	String key = iterator.next().getKey();
        	if (bankWsdlMap.get(key) != null) {
        		String excelsysBody = excelsysWsdlMap.get(key);
        		String bankBody = bankWsdlMap.get(key);
        		excelsysBody = excelsysBody.replaceAll("http://ws.excelsys.co/icbs-bus-simulator-web/", "");
        		bankBody = bankBody.replaceAll(bankUrl, "");
	        	if (!excelsysBody.equals(bankBody)) {
	        		differences.add(key);
	        	}
        	}
        }
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
	    	URLConnection con = url.openConnection();
	    	InputStream in = con.getInputStream();
	    	String encoding = con.getContentEncoding();
	    	encoding = encoding == null ? "UTF-8" : encoding;
	    	return IOUtils.toString(in, encoding);
    	}
    	catch(IOException e){
    		System.out.println(e.getMessage());
    		return null;
    	}
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
}

