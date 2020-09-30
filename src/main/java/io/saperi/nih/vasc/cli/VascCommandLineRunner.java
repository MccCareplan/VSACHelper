package io.saperi.nih.vasc.cli;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import io.saperi.nih.vasc.cli.data.TokenInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.apache.commons.cli.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.*;

@Component
@Slf4j
public class VascCommandLineRunner implements CommandLineRunner {

    private static String NLMTGTEndpoint = "https://utslogin.nlm.nih.gov/cas/v1/api-key";
    private static String NMMServerTicketEndpoint = "https://utslogin.nlm.nih.gov/cas/v1/tickets/";
    private static String NLMService = "http://umlsks.nlm.nih.gov";
    private static int TICKET_LIFE_WINDOW = 1000 * 60 * 5;   //Margin for the TGT lifespan

    private static String VSACEndpoint = "https://vsac.nlm.nih.gov/vsac/svs/RetrieveValueSet";

    private String outputDirectory;
    private OutputProcessor output = new OutputProcessor();

    private static void printHelp(Options options) {
        System.out.println("Usage: [command] [args] [options]");
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("CLI", options);
        System.out.println("Supported Commands:");
        System.out.println("  convert [valuesetid]");
        System.out.println("  fetch [valuesetid]");
        System.out.println("  init [object]");
        System.out.println("  reset");
        System.out.println("  test [testname]");
    }

    private void commandReset(CommandLine cmd) throws IOException {
        TokenInfo tok = this.getTokenInfo();
        tok.setTokenGrantedOn(null);
        tok.setTokenGrantingTicket(null);
        this.saveTokenInfo(tok);
    }

    private void commandFetch(ArrayList<String> args, CommandLine cmd) throws IOException {
        if (args.size() == 0) {
            return;
        }
        TokenInfo tok = this.getTokenInfo();
        String format = cmd.getOptionValue("f", "csv");
        for (String valueSetId : args) {
            //Fetch each value set and see what we so with it
            output.println("Fetching value set " + valueSetId);
            String vs = fetchValueSet(tok, valueSetId);

            String out;
            switch (format) {
                case "fhir-json": {
                    ValueSet hvs = this.convertVASCValueSetToFHIRValueSet(vs, valueSetId);
                    FhirContext fhirContext = FhirContext.forR4();
                    out = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(hvs);
                    break;
                }
                case "fhir-xml": {
                    ValueSet hvs = this.convertVASCValueSetToFHIRValueSet(vs, valueSetId);
                    FhirContext fhirContext = FhirContext.forR4();
                    out = fhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(hvs);
                    break;
                }
                case "csv": {
                    out = this.convertVASCValueSetToCSVValueSet(vs, valueSetId).toString();
                    break;
                }
                case "xml": {
                    out = vs;
                    break;
                }
                default: {
                    output.printException("Invalid format type: " + format);
                    return;
                }
            }
            if (output.isVerbose()) {
                System.out.println(valueSetId);
            }
            System.out.println(out);
        }
    }

    private void commandConvert(ArrayList<String> args, CommandLine cmd) throws IOException {
        if (args.size() == 0) {
            return;
        }


        TokenInfo tok = this.getTokenInfo();
        if (args.size() > 1 && cmd.hasOption("o")) {
            output.printException("Error multiple conversion targets selected with a single output file");
            return;
        }

        output.println("Fetching and converting "+ Integer.toString(args.size())+(args.size()>1?" valueset":" valuesets"));

        String format = cmd.getOptionValue("f", "csv");
        for (String valueSetId : args) {
            //Fetch each value set and see what we so with it
            output.vprintln("Fetching valueset "+valueSetId);
            String vs = fetchValueSet(tok, valueSetId);
            output.println("Converting value set "+valueSetId+" to "+format);
            String out;
            String fileName;
            String suffix;
            switch (format) {
                case "fhir+json": {
                    ValueSet hvs = this.convertVASCValueSetToFHIRValueSet(vs, valueSetId);
                    FhirContext fhirContext = FhirContext.forR4();
                    out = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(hvs);
                    suffix = "json";
                    break;
                }
                case "fhir+xml": {
                    ValueSet hvs = this.convertVASCValueSetToFHIRValueSet(vs, valueSetId);
                    FhirContext fhirContext = FhirContext.forR4();
                    out = fhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(hvs);
                    suffix = "xml";
                    break;
                }
                case "csv": {
                    out = this.convertVASCValueSetToCSVValueSet(vs, valueSetId).toString();
                    suffix = "csv";
                    break;
                }
                case "xml": {
                    out = vs;
                    suffix = "xml";
                    break;
                }
                default: {
                    output.printException("Invalid format type: " + format);
                    return;
                }
            }
            if (cmd.hasOption("o")) {
                fileName = cmd.getOptionValue("o");
            } else {
                StringBuilder bld = new StringBuilder();
                if (outputDirectory != null) {
                    bld.append(outputDirectory);
                    if (!outputDirectory.endsWith(File.separator)) {
                        bld.append(File.separator);
                    }
                }
                bld.append(valueSetId);
                bld.append(".");
                bld.append(suffix);
                fileName = bld.toString();
            }
            File file = new File(fileName);
            FileUtils.writeStringToFile(file, out, Charsets.UTF_8);
        }
    }

    private void commandInit(ArrayList<String> args, CommandLine cmd) {
        if (args.size() == 0) {
            args.add("tokens");
        }
        for (String init : args) {
            String what = init.toLowerCase();
            switch (what) {
                case "token": {
                    try {
                        this.initTokenFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "?":
                case "help": {
                    System.out.println("Valid objects are: ");
                    System.out.println("          tokens");
                    break;
                }
                default: {
                    System.out.println("Invalid object: " + init);
                    System.out.println("Use '$init help' to get a list of valid object types ");
                    break;
                }
            }
        }

    }

    private void commandTest(ArrayList<String> args, CommandLine cmd) {
        if (args.size() == 0) {
            return;
        }

        for (String test : args) {
            String what = test.toLowerCase();
            switch (what) {
                case "loadtoken": {
                    try {
                        TokenInfo tok = this.getTokenInfo();
                        System.out.println(tok.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "servicetoken": {
                    try {
                        TokenInfo tok = this.getTokenInfo();
                        String st = this.getServiceTicket(tok);
                        System.out.println(st);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "tgt": {
                    try {
                        TokenInfo tok = this.getTokenInfo();
                        this.getTGT(tok);
                        System.out.println(tok.getTokenGrantingTicket());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "savetoken": {
                    try {
                        TokenInfo tok = this.getTokenInfo();
                        tok.setTokenGrantedOn(null);
                        tok.setTokenGrantingTicket(null);
                        this.saveTokenInfo(tok);
                        System.out.println("Token updated");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "xml": {
                    this.runXMLTest();
                    break;
                }

                case "?":
                case "help": {
                    System.out.println("Valid tests are: ");
                    System.out.println("          loadToken");
                    System.out.println("          saveToken");
                    System.out.println("          serviceToken");
                    System.out.println("          TGT");
                    System.out.println("          xml");
                    break;
                }
                default: {
                    System.out.println("Invalid test: " + test);
                    System.out.println("Use 'test help' to get a list of valid tests ");
                    break;
                }
            }
        }

    }

    private StringBuilder convertVASCValueSetToCSVValueSet(String vsXML, String valueSetId) {
        //System, Version, Code, Display
        StringBuilder strBld = new StringBuilder();
        strBld.append("System,Version,Code,Display");

        try {

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(IOUtils.toInputStream(vsXML, Charsets.UTF_8));

            doc.getDocumentElement().normalize();

            NodeList valueSets = doc.getElementsByTagName("ns0:ValueSet");

            NodeList codeList = doc.getElementsByTagName("ns0:Concept");

            HashMap<String, VSACSystem> codeBySystem = new HashMap<>();

            for (int temp = 0; temp < codeList.getLength(); temp++) {

                Node nNode = codeList.item(temp);


                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;
                    strBld.append("\n");
                    strBld.append(FHIRCodeSystemMapper.getFHIRCodeSystem(eElement.getAttribute("codeSystem")));
                    strBld.append(",");
                    strBld.append(eElement.getAttribute("codeSystemVersion"));
                    strBld.append(",");
                    strBld.append(eElement.getAttribute("code"));
                    strBld.append(",");
                    strBld.append(eElement.getAttribute("displayName"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return strBld;
    }

    private ValueSet convertVASCValueSetToFHIRValueSet(String vsXML, String valueSetId) {
        ValueSet vs = new ValueSet();
        vs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        vs.setId(valueSetId);
        vs.setUrl("http://cts.nlm.nih.gov/fhir/ValueSet/" + valueSetId);
        Identifier id = new Identifier();
        id.setSystem("urn:ietf:rfc:3986");
        id.setValue(valueSetId);
        try {

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(IOUtils.toInputStream(vsXML, Charsets.UTF_8));

            doc.getDocumentElement().normalize();

            NodeList valueSets = doc.getElementsByTagName("ns0:ValueSet");
            if (valueSets.getLength() == 1) {
                Node vsn = valueSets.item(0);
                Element eElement = (Element) vsn;
                vs.setTitle(eElement.getAttribute("displayName"));
            }

            NodeList codeList = doc.getElementsByTagName("ns0:Concept");

            HashMap<String, VSACSystem> codeBySystem = new HashMap<>();

            for (int temp = 0; temp < codeList.getLength(); temp++) {

                Node nNode = codeList.item(temp);


                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;
                    String key = eElement.getAttribute("codeSystem") + "|" + eElement.getAttribute("codeSystemVersion");
                    VSACSystem sysEnt;
                    if (!codeBySystem.containsKey(key)) {
                        sysEnt = new VSACSystem();
                        sysEnt.system = eElement.getAttribute("codeSystem");
                        sysEnt.version = eElement.getAttribute("codeSystemVersion");
                        codeBySystem.put(key, sysEnt);
                    } else {
                        sysEnt = codeBySystem.get(key);
                    }
                    VSACCode cd = new VSACCode();
                    cd.code = eElement.getAttribute("code");
                    cd.display = eElement.getAttribute("displayName");
                    sysEnt.code.add(cd);
                }
            }
            // Ok Now process the array
            Set<String> keys = codeBySystem.keySet();
            for (String key : keys) {
                VSACSystem sysEnt = codeBySystem.get(key);
                ValueSet.ConceptSetComponent set = new ValueSet.ConceptSetComponent();
                String system = FHIRCodeSystemMapper.getFHIRCodeSystem(sysEnt.system);
                set.setSystem(system);
                set.setVersion(sysEnt.version);
                for (VSACCode cd : sysEnt.code) {
                    set.addConcept().setDisplay(cd.display).setCode(cd.code);
                }
                vs.getCompose().addInclude(set);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return vs;
    }

    private String fetchValueSet(TokenInfo tok, String valueSetId) throws IOException {
        String out = null;

        String serviceTicket = getServiceTicket(tok);
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            List<NameValuePair> form = new ArrayList<>();
            form.add(new BasicNameValuePair("service", NLMService));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
            StringBuilder call = new StringBuilder();
            call.append(VSACEndpoint);
            call.append("?ticket=");
            call.append(serviceTicket);
            call.append("&id=");
            call.append(valueSetId);
            HttpGet httpget = new HttpGet(call.toString());


            // Create a custom response handler
            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity responseEntity = response.getEntity();
                    return responseEntity != null ? EntityUtils.toString(responseEntity) : null;
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            };
            out = httpclient.execute(httpget, responseHandler);
        } catch (IOException e) {
            log.error("Error fetching valueset", e);
            throw e;
        }
        return out;
    }

    private Options getOptions() {
        Options options = new Options();

        Option help = new Option("h", "help", false, "print this help text");
        Option silent = new Option("q", "quit", false, "runs with no normal output");
        Option verbose = new Option("v", "verbose", false, "runs with verbose output");

        //Option TemplateDirectory (-td or -templateDirectory dir)
        //Option OutputDirectory (-od or -outputDirectory dir)
        //Option for Type (-t or -type name)
        //Options for Id (-i or -id name)
        Option outputDir = Option.builder("od").argName("directory").longOpt("outputDirectory").hasArg().desc("output directory").build();
        Option format = Option.builder("f").argName("format").longOpt("format").hasArg().desc("format to output [xml,cvs,fhir+json,fhir+xml]").build();
        Option file = Option.builder("o").argName("outputfile").longOpt("outputfile").hasArg().desc("file to create").build();
        Option input = Option.builder("i").argName("inputfile").longOpt("inputfile").hasArg().desc("input file to use (csv)").build();
        //Option valueset = Option.builder("v").argName("ValueSet").longOpt("valueset").hasArg().desc("ValueSet to use").build();
        options.addOption(help);
        options.addOption(silent);
        options.addOption(verbose);
        options.addOption(format);
        //options.addOption(valueset);
        options.addOption(file);
        options.addOption(input);
        options.addOption(outputDir);
        return options;
    }

    private String getPrimativeServiceTicket(TokenInfo tok) throws IOException {
        String serviceTicket = null;
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            List<NameValuePair> form = new ArrayList<>();
            form.add(new BasicNameValuePair("service", NLMService));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);

            HttpPost httpPost = new HttpPost(NMMServerTicketEndpoint + tok.getTokenGrantingTicket());
            httpPost.setEntity(entity);
            log.info("Executing service grant request " + httpPost.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity responseEntity = response.getEntity();
                    return responseEntity != null ? EntityUtils.toString(responseEntity) : null;
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            };
            serviceTicket = httpclient.execute(httpPost, responseHandler);
        } catch (IOException e) {
            throw e;
        }
        return serviceTicket;
    }

    private String getServiceTicket(TokenInfo tok) throws IOException {
        String out = null;
        if (!isTGTValid(tok)) {
            invalidateToken(tok);
            //Grab a new TGT
            getTGT(tok);
            try {
                saveTokenInfo(tok);
            } catch (IOException e) {
                log.error("Error Saving ticket", e);
                throw e;
            }
        }

        try {
            out = getPrimativeServiceTicket(tok);
        } catch (IOException exp) {
            invalidateToken(tok);
            getTGT(tok);
            //Second try after invalid token
            try {
                out = getPrimativeServiceTicket(tok);
            } catch (IOException e) {
                log.error("Error on secondary service ticket attempt", e);
                throw e;
            }
        }
        //Ok we have a valid TGT now get the Service Ticket
        return out;
    }

    private boolean getTGT(TokenInfo tok) {
        boolean out = false;
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            List<NameValuePair> form = new ArrayList<>();
            form.add(new BasicNameValuePair("apikey", tok.getApikey()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);

            HttpPost httpPost = new HttpPost(NLMTGTEndpoint);
            httpPost.setEntity(entity);


            // Create a custom response handler
            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity responseEntity = response.getEntity();
                    return responseEntity != null ? EntityUtils.toString(responseEntity) : null;
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            };
            String responseBody = httpclient.execute(httpPost, responseHandler);
            String TGT = getTGTFromResp(responseBody);
            if (TGT != null) {
                out = true;
                tok.setTokenGrantingTicket(TGT);
                tok.setTokenGrantedOn(new Date());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out;
    }

    private String getTGTFromResp(String responseBody) {
        String action = null;

        int indx = responseBody.indexOf("action=");
        if (indx != -1) {
            responseBody = responseBody.substring(indx + 8);
            indx = responseBody.indexOf("\"");
            action = responseBody.substring(0, indx);
            int l = action.lastIndexOf("/");
            action = action.substring(l + 1);
        }

        /*
        responseBody = responseBody.substring(52);  //Remove the weird header {fragile}
        System.out.println(responseBody);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(IOUtils.toInputStream(responseBody, Charsets.UTF_8));
        doc.getDocumentElement().normalize();
        NodeList form = doc.getElementsByTagName("form");
        if (form.getLength()>0)
        {
            Node x = form.item(0);
            action = x.getAttributes().getNamedItem("action").getNodeValue();
            System.out.println("Action = "+action);
        }
        */

        return action;
    }

    private TokenInfo getTokenInfo() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return (TokenInfo) objectMapper.readValue(new File(".vsacTokens"), TokenInfo.class);
    }

    private void initTokenFile() throws IOException {
        TokenInfo tok = new TokenInfo();
        tok.setApikey("");
        tok.setTokenGrantedOn(null);
        tok.setTokenGrantingTicket("");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new File(".vsacTokens"), tok);
    }

    private void invalidateToken(TokenInfo tok) {
        tok.setTokenGrantingTicket(null);
        tok.setTokenGrantedOn(null);
        try {
            saveTokenInfo(tok);
        } catch (IOException exp) {
            log.error("Error saving token file", exp);
        }

    }

    private boolean isDirectoryValid(String dir) {
        File file = new File(dir);
        return file.isDirectory();

    }

    private boolean isTGTValid(TokenInfo tok) {
        if (tok.getTokenGrantingTicket() != null) {
            Date now = new Date();
            if (now.getTime() - tok.getTokenGrantedOn().getTime() > TICKET_LIFE_WINDOW) {
                return true;
            }
        }
        return false;
    }

    private void loadInputFile(ArrayList<String> args, CommandLine cmd) {

        String loadFile = cmd.getOptionValue("i", "valueset_loadlist.csv");
        InputStream inputStream = null;
        try {

            inputStream = new FileInputStream(loadFile);

            if (inputStream != null) {
                Reader in = new InputStreamReader(inputStream);

                Iterable<CSVRecord> records = null;
                records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
                for (CSVRecord record : records) {
                    String oid = record.get("Oid");
                    args.add(oid);
                }
            }
            else {
                log.warn("Missing input file " + loadFile);
            }
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

    }


    // Command Line Options Handling
    void process(CommandLine cmd, Options options) throws IOException {

        processOptions(cmd);
        String[] args = cmd.getArgs();
        if (args.length>0) {
            String command = args[0].toLowerCase();
            ArrayList<String> cmdArgs = new ArrayList<>();
            if (args.length>1)
            {
                for (int i = 1;i<args.length; i++)
                {
                    cmdArgs.add(args[i]);
                }
            }
            if (cmd.hasOption("i"))
            {
                //Update commands to include valusets
                loadInputFile(cmdArgs,cmd);
            }

            switch (command)
            {
                case "init":
                {
                    commandInit(cmdArgs,cmd);
                    break;
                }
                case "help":
                {
                    printHelp(options);
                    break;
                }
                case "fetch":
                {
                    commandFetch(cmdArgs,cmd);
                    break;
                }
                case "convert":
                {
                    commandConvert(cmdArgs,cmd);
                    break;
                }
                case "reset":
                {
                    commandReset(cmd);
                    break;
                }
                case "test":
                {
                    commandTest(cmdArgs,cmd);
                    break;
                }
                default:
                {
                    System.out.println("Unknown Command: "+command);
                    break;
                }
            }
        }
    }

    private void processOptions(CommandLine cmd)
    {
        if (cmd.hasOption("q")) output.setUnmuted(false);
        if (cmd.hasOption("v")) output.setVerbose(true);
        //if (cmd.hasOption("p")) prefix = cmd.getOptionValue("p");
        //if (cmd.hasOption("s")) suffix = cmd.getOptionValue("s");
        //if (cmd.hasOption("td")) {
        //    String dir = cmd.getOptionValue("td");
        //    if (isDirectoryValid(dir))
        //    {
        //        templateDirectory = dir;
        //    }
        //    else
        //    {
        //        output.printException("Invalid template directory "+dir);
        //        return;
        //    }
        //}
        if (cmd.hasOption("od")) {
            String dir = cmd.getOptionValue("od");
            if (isDirectoryValid(dir))
            {
                outputDirectory = dir;
            }
            else
            {
                output.printException("Output directory "+dir+" is either invalid or does not exist");
                return;
            }
        }
        //if (cmd.hasOption("i")) filterId = cmd.getOptionValue("i");
        //if (cmd.hasOption("t")) filterType = cmd.getOptionValue("t");
    }

    @Override
    public void run(String...args) throws Exception {

        CommandLineParser parser = new DefaultParser();
        Options options = getOptions();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("help") || cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                printHelp(options);
                formatter.printHelp("CLI", options);
                return;
            }
            // Ok we now had the command line
            process(cmd,options);


        } catch (ParseException e) {
            System.err.println("Error parsing command line: " + e.getLocalizedMessage());
            return;
        }
    }

    private void runXMLTest() {
        String vsId="";
        ValueSet vs = new ValueSet();
        vs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        vs.setId(vsId);
        vs.setUrl("http://cts.nlm.nih.gov/fhir/ValueSet/"+vsId);
        Identifier id = new Identifier();
        id.setSystem("urn:ietf:rfc:3986");
        id.setValue(vsId);
        //ValueSet.ConceptReferenceComponent x = vs.getCompose().getInclude().get(0).addConcept();
        //x.setCode();
        //x.setDisplay();

        //Read file
        //Build basics of value set
        //Scan each Coding
        //   Build a System/Version Key
        //   Get the Tracking structure for that combo
        //       Add concept to the tracking structure
        //Loop the tracking structues
        // add an include to the compose for each

        try {
            File fXmlFile = new File("VSAC_2.16.840.1.113762.1.4.1222.159.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);

            //optional, but recommended
            //read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            doc.getDocumentElement().normalize();

            System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
            NodeList valueSets = doc.getElementsByTagName("ns0:ValueSet");
            if (valueSets.getLength()==1)
            {
                Node vsn = valueSets.item(0);
                Element eElement = (Element) vsn;
                System.out.println("Id : " + eElement.getAttribute("ID"));
                System.out.println("Version : " + eElement.getAttribute("version"));
                System.out.println("DisplayName : " + eElement.getAttribute("displayName"));
            }

            NodeList codeList = doc.getElementsByTagName("ns0:Concept");


            for (int temp = 0; temp < codeList.getLength(); temp++) {

                Node nNode = codeList.item(temp);


                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;
                    String key = eElement.getAttribute("codeSystem") + "|" + eElement.getAttribute("codeSystemVersion");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveTokenInfo(TokenInfo tok) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new File(".vsacTokens"),tok);
    }

    class VSACSystem
    {
        public String system;
        public String version;
        public ArrayList<VSACCode> code = new ArrayList<>();
    }

    class VSACCode
    {
        public String code;
        public String display;
    }
}
