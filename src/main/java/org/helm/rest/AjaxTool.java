/**
 * *****************************************************************************
 * Copyright C 2015, The Pistoia Alliance
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ****************************************************************************
 */

/**
 *
 * @author tony yuan at Scilligence
 * 
 */
package org.helm.rest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.helm.chemtoolkit.AbstractChemistryManipulator;
import org.helm.notation2.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.bson.BsonArray;
import org.bson.BsonDocument;
//import com.mysql.jdbc.Connection;

@Path("/ajaxtool")
public class AjaxTool {
    MongoDB db = null;
    static org.helm.chemtoolkit.cdk.CDKManipulator cdk = null;
    
    @GET
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_HTML,
        MediaType.TEXT_PLAIN, MediaType.MULTIPART_FORM_DATA})
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Path("/get")
    public Response CmdGet(@Context HttpServletRequest request) {
        db = new MongoDB("mongodb://localhost:27017", "HELMDB");
        Response  ret = null;
        //if (!db.IsOpen()) {
        //    ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + (db.error == null ? "" : db.error.getMessage()))).build();
        //    return ret;
        //}
        
        Map<String, String> args = getQueryParameters(request);
        try {
            ret = OnCmd(args.get("cmd"), args, request);
        } catch (Exception e) {
            ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + e.getMessage() + ", " + GetTrace(e))).build();
        }
        
        db.Close();
        return ret;
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_HTML,
        MediaType.TEXT_PLAIN, MediaType.MULTIPART_FORM_DATA})
    @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    @Path("/post")
    public Response CmdPost(@Context HttpServletRequest request) {
        db = new MongoDB("mongodb://localhost:27017", "HELMDB");     
        Response ret = null;
        //if (!db.IsOpen()) {
        //    ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + (db.error == null ? "" : db.error.getMessage()))).build();
        //    return ret;
        //}

        String cmd = getQueryParameters(request).get("cmd");
        Map<String, String> args = cmd.equals("openjsd") ? null : getFormParameters(request);
        try {
            ret = OnCmd(cmd, args, request);
        } catch (Exception e) {
            ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + e.getMessage() + ", " + GetTrace(e))).build();
        }
        
        db.Close();
        return ret;
    }

    Response OnCmd(String cmd, Map<String, String> items, HttpServletRequest request) throws Exception {
        JSONObject ret = new JSONObject();
        switch (cmd) {
            case "helm.toolkit.monomer.json": {
                ArrayList<JSONObject> ret2 = getToolkitMonomers();
                ret.put("list", ret2);
            }
            break;
            case "helm.toolkit.monomer.downloadjson": {
                ArrayList<JSONObject> ret2 = getToolkitMonomers();
                String s = "org.helm.webeditor.Monomers.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }
            
            case "helm.monomer.delall":
                ret = db.DelAll("HelmMonomers");
                break;
            case "helm.monomer.del":
                ret = db.DelRecord("HelmMonomers", Long.parseLong(items.get("id")));
                break;
            case "helm.monomer.load":
                ret = db.LoadMonomer(ToLong(items.get("id")));
                break;
            case "helm.monomer.save": {
                Map<String, String> data = SelectData(items, "id,symbol,name,naturalanalog,molfile,smiles,polymertype,monomertype,r1,r2,r3,r4,r5,author".split(","));
                long id = 0;
                if (data.containsKey("id")) {
                    id = ToLong(data.get("id"));
                    data.remove("id");
                }
                if (id == 0)
                    data.put("createddate", new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
                
                String molfile = data.get("molfile");
                String smiles = CalcSmiles(molfile);
                if (data.containsKey("smiles"))
                    data.replace("smiles", smiles);
                else
                    data.put("smiles", smiles);
                
                CheckMonomerUniqueness(id, data);
                id = db.SaveRecord("HelmMonomers", id, data);
                if (id > 0)
                    ret = db.ListMonomers(0, 0, id, null, null, null);
            }
            break;
            case "helm.monomer.suggest":
                break;
            case "helm.monomer.list": {
                int page = ToInt(items.get("page"));
                int countperpage = ToInt(items.get("countperpage"));
                String polymertype = items.get("polymertype");
                String monomertype = items.get("monomertype");
                String symbol = items.get("symbol");
                ret = db.ListMonomers(page, countperpage, 0, polymertype, monomertype, symbol);
            }
            break;
            case "helm.monomer.all": {
                ret.put("monomers", db.ReadAsJson("HelmMonomers"));
            }            
            break;
            case "helm.monomer.json": {
                ArrayList<JSONObject> ret2 = db.ReadAsJson("HelmMonomers");
                ret.put("monomers", ret2);
            }
            break;
            case "helm.monomer.savefile": {
                String ext = items.get("ext");
                if (ext != null)
                    ext = ext.toLowerCase();
                if (ext == null || !ext.equals("sdf") && !ext.equals("json"))
                    ext = "json";
                String contents;
                if (ext.equals("json"))
                    contents = db.ReadAsJson("HelmMonomers").toString();
                else
                    contents = db.ReadAsSDF("HelmMonomers", "Molfile");
                return Response
                        .ok(contents, "application/unknown")
                        .header("content-disposition", "attachment;filename=Monomers." + ext)
                        .build();
            }
            case "helm.monomer.downloadjson": {
                ArrayList<JSONObject> ret2 = db.ReadAsJson("HelmMonomers");
                String s = "org.helm.webeditor.Monomers.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }
            case "helm.monomer.importfromtoolkit":
                ret = ImportFromToolkit();
                break;
            case "helm.monomer.updatehashcode":
                ret = UpdateHashcode();
                break;
            case "helm.monomer.importfromurl": {
                String url = items.get("url");
                String contents = downloadString(url);
                ret = ImportMonomers("m.json", contents);
            }
            break;
            case "helm.monomer.uploadlib": {
                Part part = request.getPart("file");
                String filename = getFileName(part);
                String contents = getValue(part);
                ret = ImportMonomers(filename, contents);
                String s = "<html><head></head><body><textarea>" + wrapAjaxResult(ret) + "</textarea></body></html>";
                return Response.status(Response.Status.OK).entity(s).type("text/html").build();                
            }

            case "helm.rule.del":
                ret = db.DelRecord("HelmRules", Long.parseLong(items.get("id")));
                break;
            case "helm.rule.load":
                ret = db.LoadRule(ToLong(items.get("id")));
                break;
            case "helm.rule.save": {
                Map<String, String> data = SelectData(items, "id,category,name,description,script,author".split(","));
                long id = 0;
                if (data.containsKey("id")) {
                    id = ToLong(data.get("id"));
                    data.remove("id");
                }
                if (id == 0)
                    data.put("createddate", new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));

                id = db.SaveRecord("HelmRules", id, data);
                if (id > 0)
                    ret = db.ListRules(0, 0, id, null);
            }
            break;
            case "helm.rule.list": {
                int page = ToInt(items.get("page"));
                int countperpage = ToInt(items.get("countperpage"));
                String category = items.get("category");
                ret = db.ListRules(page, countperpage, 0, category);
            }
            break;
            case "helm.rule.all": {
                ret.put("rules", db.ReadAsJson("HelmRules"));
            }
            break;
            case "helm.rule.downloadjson":
            case "helm.rules.downloadjson": {
                ArrayList<JSONObject> ret2 = db.ReadAsJson("HelmRules");
                String s = "org.helm.webeditor.RuleSet.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }
            case "helm.rule.json": {
                ArrayList<JSONObject> ret2 = db.ReadAsJson("HelmRules");
                ret.put("rules", ret2);
            }
            break;

            case "openjsd": {
                ret = new JSONObject();
                Part part = request.getPart("file");
                String filename = getFileName(part);
                String contents = getValue(part);
                ret.put("filename", filename);
                ret.put("base64", EncodeBase64(contents));
                String s = "<html><head></head><body><textarea>" + wrapAjaxResult(ret) + "</textarea></body></html>";
                return Response.status(Response.Status.OK).entity(s).type("text/html").build();
            }
            case "savefile": {
                String filename = items.get("filename");
                String contents = items.get("contents");
                return Response
                        .ok(contents, "application/unknown")
                        .header("content-disposition", "attachment;filename=" + filename)
                        .build();
            }
            case "helm.properties":
                ret = CalculateProperties(items.get("helm"));
                break;
                
            case "cleanup":
                ret = Cleanup(items.get("input"), items.get("inputformat")); 
                break;

            default:
                return Response.status(Response.Status.OK).entity(wrapAjaxError("Unknown cmd: " + cmd)).build();
        }
        
        if (db != null && db.error != null) {
            if (ret == null)
                ret = new JSONObject();
            ret.put("dberror", db.error.getMessage());
        }
        return Response.status(Response.Status.OK).entity(wrapAjaxResult(ret)).build();
    }
    
    public static String downloadString(String url) {
        try {
            java.net.URL website = new java.net.URL(url);
            java.net.URLConnection connection = website.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) 
                response.append(inputLine);

            in.close();
            return response.toString();
        }
        catch (Exception e) {
            return null;
        }
    }
    
    void CheckMonomerUniqueness(long id, Map<String, String> data) throws Exception {
        // check duplicated symbol
        String symbol = data.get("symbol");
        if (symbol != null)
            symbol = symbol.trim();
        if (symbol == null || symbol.length() == 0)
            throw new Exception("Symbol cannot be blank");
        
        long tid = db.SelectID("HelmMonomers", "Symbol", symbol);
        if (tid > 0 && tid != id)
            throw new Exception("This symbol is used: " + symbol);

        // check duplicated structure
        String molfile = data.get("molfile");
        String hashcode = CalcHashcode(molfile);
        tid = db.SelectID("HelmMonomers", "Hashcode", hashcode);
        if (tid > 0 && tid != id)
            throw new Exception("Duplicated structure: " + symbol);
        
        data.put("hashcode", hashcode);
    }
    
    String CalcHashcode(String molfile)
    {
        //org.helm.notation2.tools.SMILES.convertMolToSMILESWithAtomMapping(molfile, )
        
        // this is one example implementation, using CDK canonical smiles as the hashcode
        if (cdk == null)
            cdk = new org.helm.chemtoolkit.cdk.CDKManipulator();
        
        try {
            return cdk.convertMolFile2SMILES(molfile);
        }
        catch(Exception e) {
            return null;
        }
    }
   
    String CalcSmiles(String molfile)
    {
        //org.helm.notation2.tools.SMILES.convertMolToSMILESWithAtomMapping(molfile, )
        
        // this is one example implementation, using CDK canonical smiles as the hashcode
        if (cdk == null)
            cdk = new org.helm.chemtoolkit.cdk.CDKManipulator();
        
        try {
            return cdk.convertMolFile2SMILES(molfile);
        }
        catch(Exception e) {
            return null;
        }
    }
    
    JSONObject UpdateHashcode() {
        JSONObject ret = new JSONObject();
        long[] list = db.SelectList("HELMMonomers", null, null);
        for (int i = 0; i < list.length; ++i)
        {
            String molfile = db.SelectString("HelmMonomers", "molfile", new org.bson.BsonDocument("id", new org.bson.BsonInt64(list[i])));
            String hashcode = CalcHashcode(molfile);
            Map<String, String> data = new HashMap<>();
            data.put("hashcode", hashcode);
            db.SaveRecord("HELMMonomers", list[i], data);
        }
        ret.put("msg", list.length + " updated");
        return ret;
    }
    
    Map<String, String> SelectData(Map<String, String> items, String[] keys) {
        Map<String, String> ret = new HashMap<>();
        for (String k : keys) {
            if (items.containsKey(k))
                ret.put(k, items.get(k));
            else
                ret.put(k, null);
        }
        return ret;
    }
    
    public JSONObject ImportMonomers(String filename, String contents) {
        if (filename == null || contents == null)
            return null;
            
        int n = 0;
        if (filename.endsWith(".json") || filename.endsWith(".js")) {
            BsonDocument doc = BsonDocument.parse("{\"records\":" + contents + "}");
            BsonArray list = doc.getArray("records");
            for (int i = 0; i < list.size(); ++i) {
                BsonDocument d = list.get(i).asDocument();
                Monomer monomer = json2Monomer(d);
                if (saveMonomer(monomer, getValue(d, "author"), getValue(d,"createddate", "createdDate")) > 0)
                    ++n;
            }
        }
        
        JSONObject ret = new JSONObject();
        ret.put("n", n);
        return ret;
    }
    
    String getValue(BsonDocument d, String key) {
        return getValue(d, key, null);
    }
    
    String getValue(BsonDocument d, String key1, String key2) {
        org.bson.BsonString ret = null;
        if (d.containsKey(key1))
            ret = d.getString(key1);
        else if (d.containsKey(key2))
            ret = d.getString(key2);
        return ret == null ? null : ret.getValue();
    }
    
    Monomer json2Monomer(BsonDocument d) {
        Monomer ret = new Monomer();
        
        // id,symbol,name,naturalanalog,molfile,smiles,polymertype,monomertype,r1,r2,r3,r4,r5,author
        ret.setAlternateId(getValue(d,"symbol"));
        ret.setName(getValue(d,"name"));
        ret.setNaturalAnalog(getValue(d,"naturalanalog"));
        ret.setMolfile(getValue(d,"molfile"));
        ret.setPolymerType(getValue(d,"polymertype"));
        ret.setMonomerType(getValue(d,"monomertype"));
        //if (d.containsKey("author"))
        //    ret.setAuthor(d.getString("author"));
        
        ret.setCanSMILES(getValue(d,"smiles", "canSMILES"));
        
        for (int i = 1; i <= 5; ++i) {
            Attachment att = new Attachment("R" + i, getValue(d,"r" + i));
            ret.addAttachment(att);
        }

        return ret;
    }
    
    JSONObject ImportFromToolkit() {
        int n = 0;
        try {
            //get monomer database via MonomerFacotry Singleton class
            Map<String, Map<String, Monomer>> allMonomers = MonomerFactory.getInstance().getMonomerDB();

            //loop through polymer type and monomer to build the JSON string
            Set<String> polymerTypes = allMonomers.keySet();
            for (String polymerType : polymerTypes) {
                //momomers for specific polymerType
                Map<String, Monomer> monomers = allMonomers.get(polymerType);
                Set<String> monomerIds = monomers.keySet();
                for (String monomerId : monomerIds) {
                    Monomer monomer = monomers.get(monomerId);
                    if (saveMonomer(monomer, null, null) > 0)
                        ++n;
                }
            }
        } catch (Exception e) {
        }    
        
        JSONObject ret = new JSONObject();
        ret.put("msg", n + " records imported");
        return ret;
    }
    
    ArrayList<JSONObject> getToolkitMonomers(){
        ArrayList<JSONObject> ret = new ArrayList<JSONObject>();
        
        try {
            //get monomer database via MonomerFacotry Singleton class
            Map<String, Map<String, Monomer>> allMonomers = MonomerFactory.getInstance().getMonomerDB();

            //loop through polymer type and monomer to build the JSON string
            Set<String> polymerTypes = allMonomers.keySet();
            for (String polymerType : polymerTypes) {
                //momomers for specific polymerType
                Map<String, Monomer> monomers = allMonomers.get(polymerType);
                Set<String> monomerIds = monomers.keySet();
                for (String monomerId : monomerIds) {
                    Monomer monomer = monomers.get(monomerId);
                    ret.add(monomer2Json(monomer));
                }
            }
        } catch (Exception e) {
        }
        
        return ret;
    }
    
    public static String EncodeBase64(String s) {
        if (s == null)
            return null;
        byte[] encodedBytes = Base64.encodeBase64(s.getBytes());
        return new String(encodedBytes);
    }
    
    public static String DecodeBase64(String s) {
        if (s == null)
            return null;
        byte[] decodedBytes = Base64.decodeBase64(s);
        return new String(decodedBytes);
    }
    
    long saveMonomer(Monomer m, String author, String createddate) {
        if (m == null)
            return 0;
        
        String symbol = m.getAlternateId();
        //String sql = "select ID from HelmMonomers where upper(symbol)=" + Database.SqlSafe(symbol);
        if (db.SelectID("HelmMonomers", "symbol", symbol) > 0)
            return 0;
        
        Map<String, String> ret = new HashMap();
        ret.put("symbol", symbol);
        ret.put("name", m.getName());
        ret.put("naturalanalog", m.getNaturalAnalog());
        ret.put("molfile", m.getMolfile());
        ret.put("smiles", m.getCanSMILES());
        ret.put("polymertype", m.getPolymerType());
        ret.put("monomertype", m.getMonomerType());
        ret.put("author", author);
        ret.put("createddate", createddate == null || createddate.isEmpty() ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()) : createddate);
        
        List<Attachment> al = m.getAttachmentList();
        List<String> l = new ArrayList();
        for (Attachment a : al) {
            String r = a.getLabel();
            ret.put(r, a.getCapGroupName());
        }
        return db.SaveRecord("HelmMonomers", 0, ret);
    }
    
    JSONObject monomer2Json(Monomer m) {
        JSONObject ret = new JSONObject();
        ret.put("id", m.getId());
        ret.put("symbol", m.getAlternateId());
        ret.put("name", m.getName());
        ret.put("naturalanalog", m.getNaturalAnalog());
        ret.put("molfile", m.getMolfile());
        ret.put("smiles", m.getCanSMILES());
        ret.put("polymertype", m.getPolymerType());
        ret.put("monomertype", m.getMonomerType());
        
        List<Attachment> al = m.getAttachmentList();
        List<String> l = new ArrayList();
        for (Attachment a : al) {
            ret.put(a.getLabel().toLowerCase(), a.getCapGroupName());
        }
        return ret;
    }
    
    static JSONObject Cleanup(String q, String inputformat) {
        if (StringUtils.isEmpty(q)) {
            return null;
        }
        
        JSONObject ret = new JSONObject();
        try {
            if (cdk == null)
                cdk = new org.helm.chemtoolkit.cdk.CDKManipulator();
            String molfile = null;
            if (inputformat != null && (inputformat.equals("mol") || inputformat.equals("molfile"))) {
                String smiles = cdk.convert(q, AbstractChemistryManipulator.StType.MOLFILE);
                molfile = cdk.convert(smiles, AbstractChemistryManipulator.StType.SMILES);
            }
            else {
                molfile = cdk.convert(q, AbstractChemistryManipulator.StType.SMILES);
            }
            ret.put("output", molfile);
        } catch (Exception e) {
        }
        return ret;
    }

    static JSONObject CalculateProperties(String helm) {
        if (StringUtils.isEmpty(helm)) {
            return null;
        }

        org.helm.notation2.tools.WebService webservice = new org.helm.notation2.tools.WebService();

        JSONObject ret = new JSONObject();
        try {
            ret.put("helm", helm);
            ret.put("mw", webservice.calculateMolecularWeight(helm));
            ret.put("mf", webservice.getMolecularFormula(helm));
            ret.put("ec", webservice.calculateExtinctionCoefficient(helm));
        } catch (Exception e) {
        }
        return ret;
    }

    static String getValue(Part part) throws IOException {
        if (part == null) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream(), "UTF-8"));
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[1024];
        for (int length = 0; (length = reader.read(buffer)) > 0;) {
            value.append(buffer, 0, length);
        }
        return value.toString();
    }

    static String GetTrace(Exception e) {
        StackTraceElement[] list = e.getStackTrace();
        if (list == null) {
            return null;
        }

        String s = "";
        for (int i = 0; i < list.length; ++i) {
            s += list[i].getFileName() + "->" + list[i].getClassName() + "->" + list[i].getMethodName() + ": line " + list[i].getLineNumber() + "|";
        }
        return s;
    }

    Map<String, String> getFormParameters(HttpServletRequest request) {
        Map<String, String> dict = new HashMap<>();
        Map<String, String> ret = new HashMap<>();
        String q = null;
        try {
            q = IOUtils.toString(request.getInputStream());
        } catch (Exception e) {
        }

        if (q != null && q.length() > 0) {
            dict = parseQueryString(q);
        }

        for (String k : dict.keySet()) {
            String v = dict.get(k);
            ret.put(k, v == null || v.isEmpty() ? null : v);
        }

        return ret;
    }
    
    private String getFileName(final Part part) {
        final String partHeader = part.getHeader("content-disposition");
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(
                        content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }
    
    Map<String, String> getQueryParameters(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return parseQueryString(queryString);
    }

    Map<String, String> parseQueryString(String queryString) {
        Map<String, String> queryParameters = new HashMap<>();
        if (StringUtils.isEmpty(queryString)) {
            return queryParameters;
        }

        String[] parameters = queryString.split("&");
        for (String parameter : parameters) {
            String[] keyValuePair = parameter.split("=");
            String v = keyValuePair.length < 2 ? null : keyValuePair[1];
            if (v != null) {
                try {
                    v = java.net.URLDecoder.decode(v, "UTF-8");
                } catch (Exception e) {
                }
            }
            queryParameters.put(keyValuePair[0], v);
        }
        return queryParameters;
    }

    static int ToInt(String s) {
        try {
            if (s == null || s.length() == 0) {
                return 0;
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    public static long ToLong(String s) {
        try {
            if (s == null || s.length() == 0) {
                return 0;
            }
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }
    
    // tool function to wrap HELM Editor acceptable json results
    public static String wrapAjaxResult(JSONObject ret) {
        JSONObject json = new JSONObject();
        json.put("succeed", true);
        json.put("ret", ret);
        return json.toString();
    }

    // tool function to wrap HELM Editor acceptable json results
    public static String wrapAjaxResult(java.util.ArrayList ret) {
        JSONObject json = new JSONObject();
        json.put("succeed", true);
        json.put("ret", ret);
        return json.toString();
    }

    public static String wrapAjaxError(String error) {
        JSONObject json = new JSONObject();
        json.put("succeed", false);
        json.put("error", error);
        return json.toString();
    }
}
